#!/usr/bin/env python3
"""
Generates Waffle's 3D navigation models (.glb), 12-frame pedestrian walk cycle,
and high-resolution 3D navigation puck sprites.

Outputs:
- app/src/main/assets/cars/{sedan-sports,suv-luxury,truck-flat,motorcycle,bicycle,walker}.glb
- app/src/main/assets/cars/colormap.png
- app/src/main/assets/walker/frame_{00..11}.png
- app/src/main/res/drawable-nodpi/ic_puck_{sedan,suv,pickup,bike,walk,arrow}_3d.png
- app/src/main/res/drawable-nodpi/ic_puck_dot.png
"""

from __future__ import annotations

import io
import json
import math
import struct
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
CARS_DIR = ROOT / "app" / "src" / "main" / "assets" / "cars"
WALKER_DIR = ROOT / "app" / "src" / "main" / "assets" / "walker"
DRAWABLE_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"


# ============================================================================
# 3D Vector Math
# ============================================================================

def v_add(a, b): return (a[0] + b[0], a[1] + b[1], a[2] + b[2])
def v_sub(a, b): return (a[0] - b[0], a[1] - b[1], a[2] - b[2])
def v_scale(a, s): return (a[0] * s, a[1] * s, a[2] * s)
def v_dot(a, b): return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
def v_cross(a, b): return (a[1]*b[2] - a[2]*b[1], a[2]*b[0] - a[0]*b[2], a[0]*b[1] - a[1]*b[0])
def v_len(a): return math.sqrt(max(0.0, v_dot(a, a)))
def v_norm(a):
    l = v_len(a)
    return (a[0] / l, a[1] / l, a[2] / l) if l > 1e-6 else (0.0, 1.0, 0.0)
def v_lerp(a, b, t): return (a[0]*(1-t) + b[0]*t, a[1]*(1-t) + b[1]*t, a[2]*(1-t) + b[2]*t)

def rot_x(p, deg):
    r = math.radians(deg)
    c, s = math.cos(r), math.sin(r)
    return (p[0], p[1]*c - p[2]*s, p[1]*s + p[2]*c)

def rot_y(p, deg):
    r = math.radians(deg)
    c, s = math.cos(r), math.sin(r)
    return (p[0]*c + p[2]*s, p[1], -p[0]*s + p[2]*c)

def rot_z(p, deg):
    r = math.radians(deg)
    c, s = math.cos(r), math.sin(r)
    return (p[0]*c - p[1]*s, p[0]*s + p[1]*c, p[2])


# ============================================================================
# Palette Texture Atlas & Mesh Builder
# ============================================================================

class PaletteAtlas:
    """Builds a 512x512 RGBA texture atlas of 64 material gradient swatches (8x8 grid)."""
    def __init__(self):
        self.img = Image.new("RGBA", (512, 512), (0, 0, 0, 255))
        self.pix = self.img.load()
        self.materials: dict[str, int] = {}
        self.next_id = 0

    def add_material(self, name: str, top_rgb, mid_rgb=None, bot_rgb=None) -> int:
        if name in self.materials:
            return self.materials[name]
        if mid_rgb is None:
            mid_rgb = top_rgb
            top_rgb = tuple(min(255, int(c * 1.12)) for c in mid_rgb)
        if bot_rgb is None:
            bot_rgb = tuple(max(0, int(c * 0.80)) for c in mid_rgb)
        idx = self.next_id
        self.next_id += 1
        cx = (idx % 8) * 64
        cy = (idx // 8) * 64
        for y in range(64):
            t = y / 63.0
            if t < 0.5:
                u = t * 2.0
                r = int(top_rgb[0]*(1-u) + mid_rgb[0]*u)
                g = int(top_rgb[1]*(1-u) + mid_rgb[1]*u)
                b = int(top_rgb[2]*(1-u) + mid_rgb[2]*u)
            else:
                u = (t - 0.5) * 2.0
                r = int(mid_rgb[0]*(1-u) + bot_rgb[0]*u)
                g = int(mid_rgb[1]*(1-u) + bot_rgb[1]*u)
                b = int(mid_rgb[2]*(1-u) + bot_rgb[2]*u)
            for x in range(64):
                self.pix[cx + x, cy + y] = (r, g, b, 255)
        self.materials[name] = idx
        return idx

    def uv_for(self, mat_idx: int, v_grad: float = 0.5) -> tuple[float, float]:
        cx = (mat_idx % 8) * 64
        cy = (mat_idx // 8) * 64
        u = (cx + 32.0) / 512.0
        v = (cy + 8.0 + max(0.0, min(1.0, v_grad)) * 48.0) / 512.0
        return (u, v)


class MeshBuilder:
    def __init__(self, atlas: PaletteAtlas):
        self.atlas = atlas
        self.vertices: list[tuple] = []  # (x, y, z, nx, ny, nz, u, v, is_wheel)
        self.triangles: list[tuple[int, int, int]] = []

    def add_tri(self, p0, p1, p2, mat_idx, is_wheel=False, n0=None, n1=None, n2=None, vg0=0.5, vg1=0.5, vg2=0.5):
        fn = v_norm(v_cross(v_sub(p1, p0), v_sub(p2, p0)))
        if n0 is None: n0 = fn
        if n1 is None: n1 = fn
        if n2 is None: n2 = fn
        uv0 = self.atlas.uv_for(mat_idx, vg0)
        uv1 = self.atlas.uv_for(mat_idx, vg1)
        uv2 = self.atlas.uv_for(mat_idx, vg2)
        base = len(self.vertices)
        self.vertices.append((*p0, *n0, *uv0, is_wheel))
        self.vertices.append((*p1, *n1, *uv1, is_wheel))
        self.vertices.append((*p2, *n2, *uv2, is_wheel))
        self.triangles.append((base, base + 1, base + 2))

    def add_quad(self, p0, p1, p2, p3, mat_idx, is_wheel=False, vg0=0.3, vg1=0.3, vg2=0.7, vg3=0.7):
        self.add_tri(p0, p1, p2, mat_idx, is_wheel, vg0=vg0, vg1=vg1, vg2=vg2)
        self.add_tri(p0, p2, p3, mat_idx, is_wheel, vg0=vg0, vg1=vg2, vg2=vg3)

    def add_poly(self, pts, mat_idx, is_wheel=False, vg=0.5):
        if len(pts) < 3:
            return
        for i in range(1, len(pts) - 1):
            self.add_tri(pts[0], pts[i], pts[i + 1], mat_idx, is_wheel, vg0=vg, vg1=vg, vg2=vg)

    def add_limb(self, p0, p1, r0, r1, mat_idx, sides=12, cap0=True, cap1=True, is_wheel=False):
        axis = v_sub(p1, p0)
        length = v_len(axis)
        if length < 1e-5:
            return
        fw = v_scale(axis, 1.0 / length)
        up_ref = (0.0, 0.0, 1.0) if abs(fw[1]) > 0.9 else (0.0, 1.0, 0.0)
        right = v_norm(v_cross(fw, up_ref))
        up = v_cross(right, fw)
        ring0, ring1, norms = [], [], []
        for i in range(sides):
            ang = 2.0 * math.pi * i / sides
            ca, sa = math.cos(ang), math.sin(ang)
            n = v_norm(v_add(v_scale(right, ca), v_scale(up, sa)))
            norms.append(n)
            ring0.append(v_add(p0, v_scale(n, r0)))
            ring1.append(v_add(p1, v_scale(n, r1)))
        for i in range(sides):
            j = (i + 1) % sides
            self.add_tri(ring0[i], ring1[i], ring1[j], mat_idx, is_wheel, n0=norms[i], n1=norms[i], n2=norms[j], vg0=0.35, vg1=0.65, vg2=0.65)
            self.add_tri(ring0[i], ring1[j], ring0[j], mat_idx, is_wheel, n0=norms[i], n1=norms[j], n2=norms[j], vg0=0.35, vg1=0.65, vg2=0.35)
        if cap0:
            fn = v_norm(v_cross(v_sub(ring0[1], ring0[0]), v_sub(ring0[2], ring0[0])))
            pts = ring0 if v_dot(fn, fw) < 0 else list(reversed(ring0))
            self.add_poly(pts, mat_idx, is_wheel, vg=0.3)
        if cap1:
            fn = v_norm(v_cross(v_sub(ring1[1], ring1[0]), v_sub(ring1[2], ring1[0])))
            pts = ring1 if v_dot(fn, fw) > 0 else list(reversed(ring1))
            self.add_poly(pts, mat_idx, is_wheel, vg=0.7)

    def add_ellipsoid(self, center, rx, ry, rz, mat_idx, lat_steps=10, lon_steps=16, rot_deg=(0, 0, 0)):
        cx, cy, cz = center
        rings, norms = [], []
        for i in range(lat_steps + 1):
            theta = math.pi * i / lat_steps
            st, ct = math.sin(theta), math.cos(theta)
            ring_p, ring_n = [], []
            for j in range(lon_steps):
                phi = 2.0 * math.pi * j / lon_steps
                sp, cp = math.sin(phi), math.cos(phi)
                lp = (rx * st * cp, ry * ct, rz * st * sp)
                ln = v_norm((st * cp / max(0.01, rx), ct / max(0.01, ry), st * sp / max(0.01, rz)))
                if rot_deg != (0, 0, 0):
                    lp = rot_y(rot_z(rot_x(lp, rot_deg[0]), rot_deg[2]), rot_deg[1])
                    ln = rot_y(rot_z(rot_x(ln, rot_deg[0]), rot_deg[2]), rot_deg[1])
                ring_p.append((cx + lp[0], cy + lp[1], cz + lp[2]))
                ring_n.append(ln)
            rings.append(ring_p)
            norms.append(ring_n)
        for i in range(lat_steps):
            vg0 = i / lat_steps
            vg1 = (i + 1) / lat_steps
            for j in range(lon_steps):
                k = (j + 1) % lon_steps
                p00, p01 = rings[i][j], rings[i][k]
                p10, p11 = rings[i + 1][j], rings[i + 1][k]
                n00, n01 = norms[i][j], norms[i][k]
                n10, n11 = norms[i + 1][j], norms[i + 1][k]
                if i > 0:
                    self.add_tri(p00, p01, p11, mat_idx, n0=n00, n1=n01, n2=n11, vg0=vg0, vg1=vg0, vg2=vg1)
                if i < lat_steps - 1:
                    self.add_tri(p00, p11, p10, mat_idx, n0=n00, n1=n11, n2=n10, vg0=vg0, vg1=vg1, vg2=vg1)


def add_outward_quad(mb: MeshBuilder, p0, p1, p2, p3, ref_inside, mat_idx, is_wheel=False, vg0=0.25, vg1=0.25, vg2=0.75, vg3=0.75):
    fc = (
        (p0[0] + p1[0] + p2[0] + p3[0]) * 0.25,
        (p0[1] + p1[1] + p2[1] + p3[1]) * 0.25,
        (p0[2] + p1[2] + p2[2] + p3[2]) * 0.25,
    )
    fn = v_norm(v_cross(v_sub(p1, p0), v_sub(p2, p0)))
    if v_dot(fn, v_sub(fc, ref_inside)) < 0:
        p0, p1, p2, p3 = p1, p0, p3, p2
        vg0, vg1, vg2, vg3 = vg1, vg0, vg3, vg2
        fn = v_scale(fn, -1.0)
    mb.add_tri(p0, p1, p2, mat_idx, is_wheel, n0=fn, n1=fn, n2=fn, vg0=vg0, vg1=vg1, vg2=vg2)
    mb.add_tri(p0, p2, p3, mat_idx, is_wheel, n0=fn, n1=fn, n2=fn, vg0=vg0, vg1=vg2, vg2=vg3)


def add_outward_box(mb: MeshBuilder, x0, x1, y0, y1, z0, z1, mat_idx, is_wheel=False):
    xa, xb = min(x0, x1), max(x0, x1)
    ya, yb = min(y0, y1), max(y0, y1)
    za, zb = min(z0, z1), max(z0, z1)
    center = ((xa + xb) * 0.5, (ya + yb) * 0.5, (za + zb) * 0.5)
    p000 = (xa, ya, za); p100 = (xb, ya, za); p110 = (xb, yb, za); p010 = (xa, yb, za)
    p001 = (xa, ya, zb); p101 = (xb, ya, zb); p111 = (xb, yb, zb); p011 = (xa, yb, zb)
    add_outward_quad(mb, p100, p000, p010, p110, center, mat_idx, is_wheel, 0.7, 0.7, 0.25, 0.25)
    add_outward_quad(mb, p001, p101, p111, p011, center, mat_idx, is_wheel, 0.7, 0.7, 0.25, 0.25)
    add_outward_quad(mb, p000, p100, p101, p001, center, mat_idx, is_wheel, 0.8, 0.8, 0.8, 0.8)
    add_outward_quad(mb, p010, p110, p111, p011, center, mat_idx, is_wheel, 0.15, 0.15, 0.35, 0.35)
    add_outward_quad(mb, p000, p001, p011, p010, center, mat_idx, is_wheel, 0.7, 0.7, 0.25, 0.25)
    add_outward_quad(mb, p101, p100, p110, p111, center, mat_idx, is_wheel, 0.7, 0.7, 0.25, 0.25)


def add_outward_loft(mb: MeshBuilder, rings, mat_idx_or_fn, cap_start=True, cap_end=True, is_wheel=False):
    n = len(rings[0])
    for r_idx in range(len(rings) - 1):
        rA = rings[r_idx]
        rB = rings[r_idx + 1]
        pts_all = rA + rB
        rc = (
            sum(p[0] for p in pts_all) / len(pts_all),
            sum(p[1] for p in pts_all) / len(pts_all),
            sum(p[2] for p in pts_all) / len(pts_all),
        )
        for i in range(n):
            j = (i + 1) % n
            mat = mat_idx_or_fn(r_idx, i) if callable(mat_idx_or_fn) else mat_idx_or_fn
            add_outward_quad(mb, rA[i], rB[i], rB[j], rA[j], rc, mat, is_wheel)
    all_pts = [p for r in rings for p in r]
    mc = (
        sum(p[0] for p in all_pts) / len(all_pts),
        sum(p[1] for p in all_pts) / len(all_pts),
        sum(p[2] for p in all_pts) / len(all_pts),
    )
    if cap_start:
        mat0 = mat_idx_or_fn(0, -1) if callable(mat_idx_or_fn) else mat_idx_or_fn
        r0 = rings[0]
        fn = v_norm(v_cross(v_sub(r0[1], r0[0]), v_sub(r0[2], r0[0])))
        pts = list(reversed(r0)) if v_dot(fn, v_sub(r0[0], mc)) < 0 else r0
        mb.add_poly(pts, mat0, is_wheel)
    if cap_end:
        mat1 = mat_idx_or_fn(len(rings) - 1, -2) if callable(mat_idx_or_fn) else mat_idx_or_fn
        r1 = rings[-1]
        fn = v_norm(v_cross(v_sub(r1[1], r1[0]), v_sub(r1[2], r1[0])))
        pts = list(reversed(r1)) if v_dot(fn, v_sub(r1[0], mc)) < 0 else r1
        mb.add_poly(pts, mat1, is_wheel)


# ============================================================================
# Shared Materials & Components
# ============================================================================

def setup_common_palette(atlas: PaletteAtlas):
    atlas.add_material("tire", (42, 46, 54), (24, 27, 32), (14, 16, 20))
    atlas.add_material("rim", (235, 240, 248), (185, 195, 210), (125, 135, 150))
    atlas.add_material("rim_dark", (95, 104, 118), (64, 72, 84), (40, 46, 56))
    atlas.add_material("glass", (68, 108, 142), (28, 48, 68), (16, 28, 42))
    atlas.add_material("glass_shine", (145, 198, 235), (92, 148, 188), (52, 92, 128))
    atlas.add_material("sunroof", (38, 62, 84), (18, 32, 46), (12, 20, 30))
    atlas.add_material("trim_dark", (68, 76, 88), (44, 50, 60), (26, 30, 38))
    atlas.add_material("grille", (36, 42, 50), (20, 24, 30), (10, 12, 16))
    atlas.add_material("chrome", (245, 248, 255), (200, 210, 224), (145, 155, 170))
    atlas.add_material("headlight", (255, 252, 232), (255, 240, 190), (225, 198, 135))
    atlas.add_material("taillight", (255, 72, 62), (228, 28, 32), (162, 12, 18))
    atlas.add_material("taillight_amber", (255, 196, 56), (238, 152, 22), (185, 105, 10))
    atlas.add_material("plate", (240, 244, 250), (210, 218, 228), (175, 184, 196))
    atlas.add_material("bed_liner", (52, 58, 68), (34, 39, 47), (22, 26, 32))

    atlas.add_material("sedan_body", (248, 82, 62), (214, 46, 36), (152, 24, 20))
    atlas.add_material("sedan_dark", (188, 38, 30), (148, 24, 20), (102, 14, 12))
    atlas.add_material("sedan_accent", (255, 115, 92), (235, 68, 52), (185, 36, 28))

    atlas.add_material("suv_body", (255, 208, 68), (234, 164, 28), (176, 112, 14))
    atlas.add_material("suv_dark", (198, 132, 18), (158, 98, 12), (112, 66, 8))
    atlas.add_material("suv_accent", (255, 226, 110), (245, 188, 48), (198, 134, 20))

    atlas.add_material("pickup_body", (82, 176, 238), (38, 124, 188), (22, 82, 134))
    atlas.add_material("pickup_dark", (28, 96, 150), (18, 68, 112), (12, 46, 80))
    atlas.add_material("pickup_accent", (120, 202, 252), (62, 152, 216), (34, 104, 162))

    atlas.add_material("moto_body", (246, 76, 54), (210, 42, 32), (148, 20, 18))
    atlas.add_material("moto_gold", (248, 198, 78), (212, 156, 42), (162, 112, 24))
    atlas.add_material("rider_suit", (48, 56, 68), (30, 36, 46), (18, 22, 30))
    atlas.add_material("rider_helmet", (245, 248, 252), (212, 220, 232), (168, 178, 192))
    atlas.add_material("visor", (32, 48, 66), (16, 24, 36), (8, 12, 20))

    atlas.add_material("bike_frame", (255, 196, 68), (242, 158, 38), (188, 112, 18))
    atlas.add_material("cyclist_jersey", (56, 168, 226), (30, 126, 186), (18, 86, 136))
    atlas.add_material("cyclist_stripe", (255, 206, 76), (242, 168, 42), (196, 124, 22))
    atlas.add_material("cyclist_helmet", (248, 250, 255), (218, 226, 238), (175, 186, 202))
    atlas.add_material("cyclist_shorts", (44, 52, 64), (28, 34, 44), (18, 22, 30))

    atlas.add_material("skin", (238, 184, 148), (214, 156, 118), (178, 122, 88))
    atlas.add_material("hair", (44, 38, 36), (26, 22, 22), (14, 12, 12))
    atlas.add_material("hoodie", (246, 98, 68), (222, 72, 46), (168, 44, 28))
    atlas.add_material("hoodie_dark", (196, 58, 36), (158, 40, 24), (118, 26, 16))
    atlas.add_material("backpack", (54, 66, 82), (34, 44, 58), (22, 28, 38))
    atlas.add_material("backpack_strap", (82, 96, 114), (58, 70, 86), (38, 46, 58))
    atlas.add_material("pants", (52, 66, 86), (34, 46, 62), (22, 30, 44))
    atlas.add_material("shoe", (242, 246, 252), (212, 220, 232), (172, 182, 196))
    atlas.add_material("shoe_accent", (242, 163, 60), (218, 134, 36), (172, 98, 20))


def add_detailed_wheel(mb: MeshBuilder, atlas: PaletteAtlas, cx, cy, cz, radius, width):
    m_tire = atlas.materials["tire"]
    m_rim = atlas.materials["rim"]
    m_dark = atlas.materials["rim_dark"]
    sides = 20
    x0 = cx - width / 2.0
    x1 = cx + width / 2.0
    outer_sign = -1.0 if cx < 0 else 1.0
    outer_x = x0 if cx < 0 else x1
    wc = (cx, cy, cz)

    ring0, ring1, norms = [], [], []
    rim0, rim1, hub_ring = [], [], []
    rim_r = radius * 0.64
    hub_r = radius * 0.20

    for i in range(sides):
        ang = 2.0 * math.pi * i / sides
        ca, sa = math.cos(ang), math.sin(ang)
        norms.append((0.0, ca, sa))
        ring0.append((x0, cy + ca * radius, cz + sa * radius))
        ring1.append((x1, cy + ca * radius, cz + sa * radius))
        rim0.append((outer_x, cy + ca * rim_r, cz + sa * rim_r))
        rim1.append((outer_x - outer_sign * 0.03, cy + ca * (rim_r * 0.88), cz + sa * (rim_r * 0.88)))
        hub_ring.append((outer_x + outer_sign * 0.005, cy + ca * hub_r, cz + sa * hub_r))

    for i in range(sides):
        j = (i + 1) % sides
        mb.add_tri(ring0[i], ring1[i], ring1[j], m_tire, is_wheel=True, n0=norms[i], n1=norms[i], n2=norms[j])
        mb.add_tri(ring0[i], ring1[j], ring0[j], m_tire, is_wheel=True, n0=norms[i], n1=norms[j], n2=norms[j])
        ring_out = ring1 if cx > 0 else ring0
        add_outward_quad(mb, ring_out[i], ring_out[j], rim0[j], rim0[i], wc, m_tire, is_wheel=True)
        add_outward_quad(mb, rim0[i], rim0[j], rim1[j], rim1[i], wc, m_rim, is_wheel=True)

    sx = outer_x + outer_sign * 0.004
    for sp in range(5):
        a0 = 2.0 * math.pi * sp / 5.0
        da = 0.16
        p_in0 = (sx, cy + math.cos(a0 - da)*hub_r*0.8, cz + math.sin(a0 - da)*hub_r*0.8)
        p_in1 = (sx, cy + math.cos(a0 + da)*hub_r*0.8, cz + math.sin(a0 + da)*hub_r*0.8)
        p_out1 = (sx, cy + math.cos(a0 + da*0.7)*rim_r*0.95, cz + math.sin(a0 + da*0.7)*rim_r*0.95)
        p_out0 = (sx, cy + math.cos(a0 - da*0.7)*rim_r*0.95, cz + math.sin(a0 - da*0.7)*rim_r*0.95)
        add_outward_quad(mb, p_in0, p_in1, p_out1, p_out0, wc, m_rim, is_wheel=True)


def add_spoke_wheel(mb: MeshBuilder, atlas: PaletteAtlas, cy, cz, radius, tire_r, num_spokes=10):
    m_tire = atlas.materials["tire"]
    m_rim = atlas.materials["rim"]
    m_hub = atlas.materials["chrome"]
    seg_u, seg_v = 24, 10
    rim_rad = radius - tire_r * 1.15
    for i in range(seg_u):
        i1 = (i + 1) % seg_u
        a0, a1 = 2.0 * math.pi * i / seg_u, 2.0 * math.pi * i1 / seg_u
        ca0, sa0, ca1, sa1 = math.cos(a0), math.sin(a0), math.cos(a1), math.sin(a1)
        for j in range(seg_v):
            j1 = (j + 1) % seg_v
            b0, b1 = 2.0 * math.pi * j / seg_v, 2.0 * math.pi * j1 / seg_v
            cb0, sb0, cb1, sb1 = math.cos(b0), math.sin(b0), math.cos(b1), math.sin(b1)
            def pt(ca, sa, cb, sb):
                r = radius + tire_r * cb
                return (tire_r * sb, cy + r * ca, cz + r * sa)
            def nm(ca, sa, cb, sb):
                return v_norm((sb, ca * cb, sa * cb))
            p00, p10 = pt(ca0, sa0, cb0, sb0), pt(ca1, sa1, cb0, sb0)
            p11, p01 = pt(ca1, sa1, cb1, sb1), pt(ca0, sa0, cb1, sb1)
            n00, n10 = nm(ca0, sa0, cb0, sb0), nm(ca1, sa1, cb0, sb0)
            n11, n01 = nm(ca1, sa1, cb1, sb1), nm(ca0, sa0, cb1, sb1)
            mat = m_rim if cb0 < -0.35 else m_tire
            mb.add_tri(p00, p10, p11, mat, is_wheel=True, n0=n00, n1=n10, n2=n11)
            mb.add_tri(p00, p11, p01, mat, is_wheel=True, n0=n00, n1=n11, n2=n01)

    mb.add_limb((-0.09, cy, cz), (0.09, cy, cz), 0.045, 0.045, m_hub, sides=10, is_wheel=True)
    for s in range(num_spokes):
        ang = 2.0 * math.pi * s / num_spokes
        py = cy + math.cos(ang) * rim_rad
        pz = cz + math.sin(ang) * rim_rad
        hx = 0.035 if s % 2 == 0 else -0.035
        mb.add_limb((hx, cy, cz), (0.0, py, pz), 0.015, 0.013, m_rim, sides=6, cap0=False, cap1=False, is_wheel=True)


def make_car_ring(z, half_w, bot_y, belt_y, deck_y, flare=0.0, bevel=0.08):
    w_bot = half_w * 0.86
    w_mid = half_w + flare
    w_belt = half_w * 0.96
    w_deck = max(0.08, half_w - bevel)
    mid_y = bot_y + (belt_y - bot_y) * 0.48
    return [
        (-w_bot, bot_y, z),
        ( w_bot, bot_y, z),
        ( w_mid, bot_y + 0.06, z),
        ( w_mid, mid_y, z),
        ( w_belt, belt_y, z),
        ( w_deck, deck_y, z),
        (-w_deck, deck_y, z),
        (-w_belt, belt_y, z),
        (-w_mid, mid_y, z),
        (-w_mid, bot_y + 0.06, z),
    ]


def add_clean_cabin(
    mb: MeshBuilder,
    atlas: PaletteAtlas,
    z_rb, z_rt, z_ft, z_fb,
    w_rb, w_rt, w_ft, w_fb,
    y_b, y_t,
    m_body, m_dark, m_glass, m_shine, m_sunroof, m_chrome,
    has_sunroof=True, has_b_pillar=True
):
    cc = (0.0, (y_b + y_t) * 0.5, (z_rb + z_fb) * 0.5)

    def rear_pt(u, v):
        wb = w_rb * (1 - v) + w_rt * v
        yb = y_b  * (1 - v) + y_t  * v
        zb = z_rb * (1 - v) + z_rt * v
        return (u * wb, yb, zb)

    for s0, s1 in ((-1.0, -0.76), (0.76, 1.0)):
        add_outward_quad(mb, rear_pt(s0, 0.0), rear_pt(s1, 0.0), rear_pt(s1, 1.0), rear_pt(s0, 1.0), cc, m_body)
    add_outward_quad(mb, rear_pt(-0.76, 0.0), rear_pt(0.76, 0.0), rear_pt(0.76, 0.10), rear_pt(-0.76, 0.10), cc, m_body)
    add_outward_quad(mb, rear_pt(-0.76, 0.90), rear_pt(0.76, 0.90), rear_pt(0.76, 1.0), rear_pt(-0.76, 1.0), cc, m_body)
    add_outward_quad(mb, rear_pt(-0.76, 0.10), rear_pt(0.76, 0.10), rear_pt(0.76, 0.64), rear_pt(-0.76, 0.64), cc, m_glass, vg0=0.85, vg1=0.85, vg2=0.35, vg3=0.35)
    add_outward_quad(mb, rear_pt(-0.76, 0.64), rear_pt(0.76, 0.64), rear_pt(0.76, 0.78), rear_pt(-0.76, 0.78), cc, m_shine)
    add_outward_quad(mb, rear_pt(-0.76, 0.78), rear_pt(0.76, 0.78), rear_pt(0.76, 0.90), rear_pt(-0.76, 0.90), cc, m_glass, vg0=0.25, vg1=0.25, vg2=0.15, vg3=0.15)

    def front_pt(u, v):
        wb = w_fb * (1 - v) + w_ft * v
        yb = y_b  * (1 - v) + y_t  * v
        zb = z_fb * (1 - v) + z_ft * v
        return (u * wb, yb, zb)

    for s0, s1 in ((-1.0, -0.78), (0.78, 1.0)):
        add_outward_quad(mb, front_pt(s0, 0.0), front_pt(s1, 0.0), front_pt(s1, 1.0), front_pt(s0, 1.0), cc, m_body)
    add_outward_quad(mb, front_pt(-0.78, 0.0), front_pt(0.78, 0.0), front_pt(0.78, 0.10), front_pt(-0.78, 0.10), cc, m_body)
    add_outward_quad(mb, front_pt(-0.78, 0.90), front_pt(0.78, 0.90), front_pt(0.78, 1.0), front_pt(-0.78, 1.0), cc, m_body)
    add_outward_quad(mb, front_pt(-0.78, 0.10), front_pt(0.78, 0.10), front_pt(0.78, 0.90), front_pt(-0.78, 0.90), cc, m_glass)

    def roof_pt(u, v):
        wt = w_rt * (1 - v) + w_ft * v
        zb = z_rt * (1 - v) + z_ft * v
        crown = (1.0 - u*u) * 0.025
        return (u * wt, y_t + crown, zb)

    if has_sunroof:
        for s0, s1 in ((-1.0, -0.62), (0.62, 1.0)):
            add_outward_quad(mb, roof_pt(s0, 0.0), roof_pt(s1, 0.0), roof_pt(s1, 1.0), roof_pt(s0, 1.0), cc, m_body)
        add_outward_quad(mb, roof_pt(-0.62, 0.0), roof_pt(0.62, 0.0), roof_pt(0.62, 0.22), roof_pt(-0.62, 0.22), cc, m_body)
        add_outward_quad(mb, roof_pt(-0.62, 0.80), roof_pt(0.62, 0.80), roof_pt(0.62, 1.0), roof_pt(-0.62, 1.0), cc, m_body)
        add_outward_quad(mb, roof_pt(-0.62, 0.22), roof_pt(0.62, 0.22), roof_pt(0.62, 0.80), roof_pt(-0.62, 0.80), cc, m_sunroof)
    else:
        add_outward_quad(mb, roof_pt(-1.0, 0.0), roof_pt(1.0, 0.0), roof_pt(1.0, 1.0), roof_pt(-1.0, 1.0), cc, m_body)

    for side in (-1.0, 1.0):
        def side_pt(t_z, t_y):
            z_bot = z_rb * (1 - t_z) + z_fb * t_z
            z_top = z_rt * (1 - t_z) + z_ft * t_z
            w_bot = w_rb * (1 - t_z) + w_fb * t_z
            w_top = w_rt * (1 - t_z) + w_ft * t_z
            z = z_bot * (1 - t_y) + z_top * t_y
            w = w_bot * (1 - t_y) + w_top * t_y
            y = y_b * (1 - t_y) + y_t * t_y
            return (side * w, y, z)

        add_outward_quad(mb, side_pt(0.0, 0.0), side_pt(1.0, 0.0), side_pt(1.0, 0.08), side_pt(0.0, 0.08), cc, m_chrome)
        add_outward_quad(mb, side_pt(0.0, 0.88), side_pt(1.0, 0.88), side_pt(1.0, 1.0), side_pt(0.0, 1.0), cc, m_body)
        add_outward_quad(mb, side_pt(0.0, 0.08), side_pt(0.14, 0.08), side_pt(0.14, 0.88), side_pt(0.0, 0.88), cc, m_body)
        add_outward_quad(mb, side_pt(0.88, 0.08), side_pt(1.0, 0.08), side_pt(1.0, 0.88), side_pt(0.88, 0.88), cc, m_body)
        if has_b_pillar:
            add_outward_quad(mb, side_pt(0.14, 0.08), side_pt(0.47, 0.08), side_pt(0.47, 0.88), side_pt(0.14, 0.88), cc, m_glass)
            add_outward_quad(mb, side_pt(0.47, 0.08), side_pt(0.55, 0.08), side_pt(0.55, 0.88), side_pt(0.47, 0.88), cc, m_dark)
            add_outward_quad(mb, side_pt(0.55, 0.08), side_pt(0.88, 0.08), side_pt(0.88, 0.88), side_pt(0.55, 0.88), cc, m_glass)
        else:
            add_outward_quad(mb, side_pt(0.14, 0.08), side_pt(0.88, 0.08), side_pt(0.88, 0.88), side_pt(0.14, 0.88), cc, m_glass)


# ============================================================================
# 6 Vehicle & Character 3D Model Builders
# ============================================================================

def build_sedan(atlas: PaletteAtlas) -> MeshBuilder:
    mb = MeshBuilder(atlas)
    m_body = atlas.materials["sedan_body"]
    m_dark = atlas.materials["sedan_dark"]
    m_acc = atlas.materials["sedan_accent"]
    m_trim = atlas.materials["trim_dark"]
    m_grille = atlas.materials["grille"]
    m_chrome = atlas.materials["chrome"]
    m_glass = atlas.materials["glass"]
    m_shine = atlas.materials["glass_shine"]
    m_sunroof = atlas.materials["sunroof"]
    m_head = atlas.materials["headlight"]
    m_tail = atlas.materials["taillight"]
    m_plate = atlas.materials["plate"]

    stations = [
        (-1.30, 0.57, 0.20, 0.56, 0.69, 0.00, 0.07),
        (-1.14, 0.63, 0.17, 0.59, 0.72, 0.02, 0.06),
        (-0.82, 0.65, 0.16, 0.61, 0.73, 0.05, 0.06),
        (-0.28, 0.64, 0.16, 0.61, 0.73, 0.01, 0.06),
        ( 0.28, 0.64, 0.16, 0.60, 0.72, 0.01, 0.06),
        ( 0.82, 0.65, 0.16, 0.58, 0.69, 0.05, 0.06),
        ( 1.16, 0.60, 0.18, 0.53, 0.63, 0.01, 0.08),
        ( 1.32, 0.51, 0.20, 0.46, 0.55, 0.00, 0.10),
    ]
    rings = [make_car_ring(*s) for s in stations]
    def body_mat(r_idx, edge):
        if edge in (0, 1, 9): return m_trim
        if edge in (2, 8): return m_dark
        if edge == 5: return m_acc
        return m_body
    add_outward_loft(mb, rings, body_mat)

    add_clean_cabin(
        mb, atlas,
        z_rb=-0.92, z_rt=-0.46, z_ft=0.06, z_fb=0.46,
        w_rb=0.52,  w_rt=0.43,  w_ft=0.41, w_fb=0.49,
        y_b=0.72,   y_t=1.08,
        m_body=m_body, m_dark=m_dark, m_glass=m_glass, m_shine=m_shine,
        m_sunroof=m_sunroof, m_chrome=m_chrome,
        has_sunroof=True, has_b_pillar=True
    )

    add_outward_box(mb, -0.22,  0.22, 0.66, 0.715, 0.46, 1.14, m_acc)
    add_outward_box(mb, -0.30, -0.26, 0.66, 0.705, 0.48, 1.18, m_dark)
    add_outward_box(mb,  0.26,  0.30, 0.66, 0.705, 0.48, 1.18, m_dark)

    add_outward_box(mb, -0.40, -0.34, 0.68, 0.79, -1.22, -1.10, m_trim)
    add_outward_box(mb,  0.34,  0.40, 0.68, 0.79, -1.22, -1.10, m_trim)
    add_outward_box(mb, -0.55,  0.55, 0.78, 0.82, -1.25, -1.08, m_acc)

    for side in (-1.0, 1.0):
        xa, xb = min(side*0.49, side*0.68), max(side*0.49, side*0.68)
        add_outward_box(mb, xa, xb, 0.74, 0.83, 0.24, 0.36, m_body)

    add_outward_box(mb, -0.54, 0.54, 0.18, 0.34, -1.34, -1.26, m_trim)
    mb.add_limb((-0.34, 0.24, -1.26), (-0.34, 0.24, -1.37), 0.048, 0.048, m_chrome, sides=12)
    mb.add_limb(( 0.34, 0.24, -1.26), ( 0.34, 0.24, -1.37), 0.048, 0.048, m_chrome, sides=12)
    add_outward_box(mb, -0.55, -0.18, 0.50, 0.62, -1.33, -1.27, m_tail)
    add_outward_box(mb,  0.18,  0.55, 0.50, 0.62, -1.33, -1.27, m_tail)
    add_outward_box(mb, -0.55,  0.55, 0.57, 0.62, -1.33, -1.27, m_tail)
    add_outward_box(mb, -0.17,  0.17, 0.35, 0.46, -1.34, -1.28, m_plate)

    add_outward_box(mb, -0.50,  0.50, 0.18, 0.27, 1.24, 1.35, m_trim)
    add_outward_box(mb, -0.28,  0.28, 0.28, 0.46, 1.26, 1.34, m_grille)
    add_outward_box(mb, -0.52, -0.26, 0.44, 0.56, 1.18, 1.33, m_head)
    add_outward_box(mb,  0.26,  0.52, 0.44, 0.56, 1.18, 1.33, m_head)

    for wx in (-0.63, 0.63):
        for wz in (-0.82, 0.82):
            add_detailed_wheel(mb, atlas, wx, 0.31, wz, 0.31, 0.22)
    return mb


def build_suv(atlas: PaletteAtlas) -> MeshBuilder:
    mb = MeshBuilder(atlas)
    m_body = atlas.materials["suv_body"]
    m_dark = atlas.materials["suv_dark"]
    m_acc = atlas.materials["suv_accent"]
    m_trim = atlas.materials["trim_dark"]
    m_grille = atlas.materials["grille"]
    m_chrome = atlas.materials["chrome"]
    m_glass = atlas.materials["glass"]
    m_shine = atlas.materials["glass_shine"]
    m_sunroof = atlas.materials["sunroof"]
    m_head = atlas.materials["headlight"]
    m_tail = atlas.materials["taillight"]
    m_plate = atlas.materials["plate"]

    stations = [
        (-1.32, 0.64, 0.24, 0.68, 0.82, 0.00, 0.06),
        (-1.18, 0.68, 0.22, 0.70, 0.84, 0.02, 0.05),
        (-0.84, 0.70, 0.22, 0.72, 0.85, 0.05, 0.05),
        (-0.25, 0.68, 0.22, 0.72, 0.85, 0.01, 0.05),
        ( 0.35, 0.68, 0.22, 0.71, 0.84, 0.01, 0.05),
        ( 0.84, 0.70, 0.22, 0.69, 0.81, 0.05, 0.05),
        ( 1.18, 0.64, 0.23, 0.63, 0.75, 0.01, 0.07),
        ( 1.32, 0.56, 0.25, 0.55, 0.67, 0.00, 0.09),
    ]
    rings = [make_car_ring(*s) for s in stations]
    def body_mat(r_idx, edge):
        if edge in (0, 1, 2, 8, 9): return m_trim
        if edge == 5: return m_acc
        return m_body
    add_outward_loft(mb, rings, body_mat)

    add_clean_cabin(
        mb, atlas,
        z_rb=-1.18, z_rt=-0.86, z_ft=0.10, z_fb=0.48,
        w_rb=0.58,  w_rt=0.49,  w_ft=0.47, w_fb=0.55,
        y_b=0.83,   y_t=1.22,
        m_body=m_body, m_dark=m_dark, m_glass=m_glass, m_shine=m_shine,
        m_sunroof=m_sunroof, m_chrome=m_chrome,
        has_sunroof=True, has_b_pillar=True
    )

    add_outward_box(mb, -0.28, 0.28, 0.78, 0.835, 0.48, 1.18, m_acc)

    for side in (-1.0, 1.0):
        rx = side * 0.43
        add_outward_box(mb, rx - 0.025, rx + 0.025, 1.24, 1.29, -0.82, 0.06, m_chrome)
    add_outward_box(mb, -0.43, 0.43, 1.26, 1.29, -0.70, -0.64, m_trim)
    add_outward_box(mb, -0.43, 0.43, 1.26, 1.29, -0.06,  0.00, m_trim)

    for side in (-1.0, 1.0):
        xa, xb = min(side*0.55, side*0.74), max(side*0.55, side*0.74)
        add_outward_box(mb, xa, xb, 0.84, 0.94, 0.26, 0.38, m_body)

    add_outward_box(mb, -0.58, 0.58, 0.22, 0.38, -1.35, -1.28, m_trim)
    add_outward_box(mb, -0.38, 0.38, 0.22, 0.32, -1.36, -1.30, m_chrome)
    add_outward_box(mb, -0.62, -0.22, 0.62, 0.76, -1.34, -1.28, m_tail)
    add_outward_box(mb,  0.22,  0.62, 0.62, 0.76, -1.34, -1.28, m_tail)
    add_outward_box(mb, -0.24,  0.24, 0.68, 0.73, -1.34, -1.29, m_chrome)
    add_outward_box(mb, -0.18,  0.18, 0.46, 0.58, -1.34, -1.29, m_plate)

    add_outward_box(mb, -0.34,  0.34, 0.38, 0.58, 1.28, 1.34, m_grille)
    add_outward_box(mb, -0.36,  0.36, 0.23, 0.34, 1.28, 1.35, m_chrome)
    add_outward_box(mb, -0.58, -0.32, 0.52, 0.65, 1.20, 1.33, m_head)
    add_outward_box(mb,  0.32,  0.58, 0.52, 0.65, 1.20, 1.33, m_head)

    for wx in (-0.68, 0.68):
        for wz in (-0.84, 0.84):
            add_detailed_wheel(mb, atlas, wx, 0.35, wz, 0.35, 0.25)
    return mb


def build_pickup(atlas: PaletteAtlas) -> MeshBuilder:
    mb = MeshBuilder(atlas)
    m_body = atlas.materials["pickup_body"]
    m_dark = atlas.materials["pickup_dark"]
    m_acc = atlas.materials["pickup_accent"]
    m_trim = atlas.materials["trim_dark"]
    m_grille = atlas.materials["grille"]
    m_chrome = atlas.materials["chrome"]
    m_glass = atlas.materials["glass"]
    m_shine = atlas.materials["glass_shine"]
    m_sunroof = atlas.materials["sunroof"]
    m_head = atlas.materials["headlight"]
    m_tail = atlas.materials["taillight"]
    m_plate = atlas.materials["plate"]
    m_bed = atlas.materials["bed_liner"]

    front_stations = [
        (-0.18, 0.68, 0.24, 0.68, 0.82, 0.01, 0.05),
        ( 0.35, 0.68, 0.24, 0.68, 0.82, 0.01, 0.05),
        ( 0.86, 0.70, 0.24, 0.67, 0.80, 0.05, 0.05),
        ( 1.20, 0.65, 0.25, 0.62, 0.75, 0.01, 0.07),
        ( 1.34, 0.58, 0.26, 0.55, 0.67, 0.00, 0.09),
    ]
    rings = [make_car_ring(*s) for s in front_stations]
    def body_mat(r_idx, edge):
        if edge in (0, 1, 2, 8, 9): return m_trim
        if edge == 5: return m_acc
        return m_body
    add_outward_loft(mb, rings, body_mat)

    add_clean_cabin(
        mb, atlas,
        z_rb=-0.18, z_rt=-0.10, z_ft=0.22, z_fb=0.56,
        w_rb=0.58,  w_rt=0.49,  w_ft=0.48, w_fb=0.55,
        y_b=0.81,   y_t=1.22,
        m_body=m_body, m_dark=m_dark, m_glass=m_glass, m_shine=m_shine,
        m_sunroof=m_sunroof, m_chrome=m_chrome,
        has_sunroof=True, has_b_pillar=False
    )

    add_outward_box(mb, -0.28, 0.28, 0.78, 0.83, 0.56, 1.22, m_acc)

    bed_z0, bed_z1 = -1.34, -0.18
    add_outward_box(mb, -0.62, 0.62, 0.24, 0.48, bed_z0, bed_z1, m_trim)
    add_outward_box(mb, -0.56, 0.56, 0.48, 0.53, bed_z0 + 0.06, bed_z1, m_bed)
    for rx in (-0.38, -0.19, 0.0, 0.19, 0.38):
        add_outward_box(mb, rx - 0.025, rx + 0.025, 0.53, 0.56, bed_z0 + 0.10, bed_z1 - 0.06, m_trim)
    for side in (-1.0, 1.0):
        xa, xb = min(side*0.52, side*0.69), max(side*0.52, side*0.69)
        add_outward_box(mb, xa, xb, 0.46, 0.82, bed_z0, bed_z1, m_body)
        add_outward_box(mb, xa - 0.005, xb + 0.005, 0.81, 0.845, bed_z0, bed_z1, m_trim)
        wxa, wxb = min(side*0.39, side*0.52), max(side*0.39, side*0.52)
        add_outward_box(mb, wxa, wxb, 0.52, 0.67, -1.04, -0.64, m_bed)

    add_outward_box(mb, -0.66, 0.66, 0.46, 0.82, bed_z0 - 0.02, bed_z0 + 0.08, m_body)
    add_outward_box(mb, -0.66, 0.66, 0.81, 0.845, bed_z0 - 0.02, bed_z0 + 0.08, m_trim)
    add_outward_box(mb, -0.14, 0.14, 0.70, 0.76, bed_z0 - 0.035, bed_z0, m_trim)
    add_outward_box(mb, -0.44, 0.44, 0.53, 0.65, bed_z0 - 0.03, bed_z0, m_dark)

    for side in (-1.0, 1.0):
        sx = side * 0.54
        mb.add_limb((sx, 0.83, -0.24), (side * 0.46, 1.22, -0.22), 0.038, 0.035, m_trim, sides=10)
        mb.add_limb((side * 0.46, 1.20, -0.22), (sx, 0.83, -0.72), 0.032, 0.032, m_trim, sides=10)
    mb.add_limb((-0.46, 1.21, -0.22), (0.46, 1.21, -0.22), 0.036, 0.036, m_trim, sides=10)

    add_outward_box(mb, -0.68, 0.68, 0.24, 0.40, bed_z0 - 0.08, bed_z0, m_chrome)
    add_outward_box(mb, -0.22, 0.22, 0.25, 0.41, bed_z0 - 0.09, bed_z0, m_trim)
    add_outward_box(mb, -0.68, -0.53, 0.50, 0.78, bed_z0 - 0.04, bed_z0 + 0.02, m_tail)
    add_outward_box(mb,  0.53,  0.68, 0.50, 0.78, bed_z0 - 0.04, bed_z0 + 0.02, m_tail)
    add_outward_box(mb, -0.16,  0.16, 0.28, 0.39, bed_z0 - 0.095, bed_z0 - 0.04, m_plate)

    for side in (-1.0, 1.0):
        xa, xb = min(side*0.56, side*0.76), max(side*0.56, side*0.76)
        add_outward_box(mb, xa, xb, 0.84, 0.95, 0.32, 0.44, m_trim)

    add_outward_box(mb, -0.38,  0.38, 0.40, 0.62, 1.30, 1.36, m_grille)
    add_outward_box(mb, -0.62,  0.62, 0.24, 0.38, 1.28, 1.37, m_chrome)
    add_outward_box(mb, -0.58, -0.34, 0.50, 0.65, 1.22, 1.35, m_head)
    add_outward_box(mb,  0.34,  0.58, 0.50, 0.65, 1.22, 1.35, m_head)

    for wx in (-0.68, 0.68):
        for wz in (-0.84, 0.86):
            add_detailed_wheel(mb, atlas, wx, 0.36, wz, 0.36, 0.26)
    return mb


def build_motorcycle(atlas: PaletteAtlas) -> MeshBuilder:
    mb = MeshBuilder(atlas)
    m_body = atlas.materials["moto_body"]
    m_gold = atlas.materials["moto_gold"]
    m_trim = atlas.materials["trim_dark"]
    m_chrome = atlas.materials["chrome"]
    m_tail = atlas.materials["taillight"]
    m_head = atlas.materials["headlight"]
    m_glass = atlas.materials["glass"]
    m_suit = atlas.materials["rider_suit"]
    m_helmet = atlas.materials["rider_helmet"]
    m_visor = atlas.materials["visor"]

    add_detailed_wheel(mb, atlas, -0.02, 0.35, -0.82, 0.35, 0.30)
    add_detailed_wheel(mb, atlas,  0.02, 0.35, -0.82, 0.35, 0.30)
    add_detailed_wheel(mb, atlas, -0.01, 0.35,  0.92, 0.35, 0.22)
    add_detailed_wheel(mb, atlas,  0.01, 0.35,  0.92, 0.35, 0.22)
    mb.add_ellipsoid((0.0, 0.64, 0.94), 0.14, 0.12, 0.28, m_body, rot_deg=(16, 0, 0))

    for side in (-1.0, 1.0):
        mb.add_limb((side*0.17, 0.35, -0.82), (side*0.18, 0.44, -0.20), 0.048, 0.058, m_trim, sides=10)
        mb.add_limb((side*0.20, 0.38, -0.24), (side*0.26, 0.62, -0.86), 0.054, 0.072, m_chrome, sides=12)
        mb.add_limb((side*0.26, 0.62, -0.86), (side*0.26, 0.64, -0.94), 0.072, 0.060, m_trim, sides=12)

    add_outward_box(mb, -0.22, 0.22, 0.28, 0.62, -0.22, 0.48, m_trim)
    for side in (-1.0, 1.0):
        mb.add_ellipsoid((side*0.25, 0.62, 0.36), 0.11, 0.22, 0.40, m_body, rot_deg=(14, 0, -side*12))
    mb.add_ellipsoid((0.0, 0.74, 0.16), 0.28, 0.15, 0.36, m_body)
    mb.add_ellipsoid((0.0, 0.80, -0.68), 0.23, 0.12, 0.32, m_body, rot_deg=(-12, 0, 0))
    add_outward_box(mb, -0.15, 0.15, 0.75, 0.85, -0.98, -0.84, m_tail)
    add_outward_box(mb, -0.10, 0.10, 0.58, 0.74, -1.02, -0.92, m_trim)

    mb.add_ellipsoid((0.0, 0.78, 0.74), 0.31, 0.19, 0.32, m_body, rot_deg=(16, 0, 0))
    mb.add_ellipsoid((0.0, 0.90, 0.62), 0.22, 0.11, 0.26, m_glass, rot_deg=(26, 0, 0))
    add_outward_box(mb, -0.20, 0.20, 0.72, 0.84, 0.90, 1.02, m_head)
    for side in (-1.0, 1.0):
        mb.add_limb((side*0.14, 0.35, 0.92), (side*0.16, 0.84, 0.50), 0.044, 0.048, m_gold, sides=10)
        mb.add_limb((side*0.12, 0.84, 0.48), (side*0.45, 0.82, 0.38), 0.036, 0.036, m_trim, sides=10)
        mb.add_limb((side*0.26, 0.86, 0.66), (side*0.48, 0.94, 0.58), 0.028, 0.046, m_trim, sides=8)

    hip = (0.0, 0.74, -0.32)
    shoulder_c = (0.0, 1.04, -0.08)
    mb.add_ellipsoid(hip, 0.22, 0.13, 0.18, m_suit)
    mb.add_limb(hip, shoulder_c, 0.19, 0.22, m_suit, sides=16, cap1=False)
    mb.add_ellipsoid(shoulder_c, 0.24, 0.095, 0.15, m_suit)
    mb.add_ellipsoid((0.0, 0.94, -0.20), 0.12, 0.16, 0.09, m_body, rot_deg=(36, 0, 0))

    for side in (-1.0, 1.0):
        sh = (side * 0.23, 1.03, -0.07)
        elb = (side * 0.37, 0.90, 0.18)
        hand = (side * 0.39, 0.83, 0.39)
        mb.add_ellipsoid(sh, 0.088, 0.088, 0.088, m_body)
        mb.add_limb(sh, elb, 0.074, 0.062, m_suit, sides=10)
        mb.add_limb(elb, hand, 0.060, 0.050, m_suit, sides=10)
        mb.add_ellipsoid(hand, 0.056, 0.052, 0.065, m_trim)

        hp = (side * 0.18, 0.72, -0.30)
        knee = (side * 0.29, 0.60, 0.06)
        foot = (side * 0.23, 0.38, -0.22)
        mb.add_limb(hp, knee, 0.096, 0.076, m_suit, sides=10)
        mb.add_limb(knee, foot, 0.072, 0.058, m_suit, sides=10)
        mb.add_ellipsoid((side * 0.23, 0.36, -0.18), 0.064, 0.052, 0.12, m_trim)

    head_c = (0.0, 1.15, 0.00)
    mb.add_ellipsoid(head_c, 0.172, 0.178, 0.210, m_helmet, rot_deg=(16, 0, 0))
    mb.add_ellipsoid((0.0, 1.16, 0.00), 0.052, 0.182, 0.214, m_body, rot_deg=(16, 0, 0))
    mb.add_ellipsoid((0.0, 1.15, 0.07), 0.165, 0.088, 0.165, m_visor, rot_deg=(16, 0, 0))
    return mb


def build_bicycle(atlas: PaletteAtlas) -> MeshBuilder:
    mb = MeshBuilder(atlas)
    m_frame = atlas.materials["bike_frame"]
    m_trim = atlas.materials["trim_dark"]
    m_chrome = atlas.materials["chrome"]
    m_jersey = atlas.materials["cyclist_jersey"]
    m_stripe = atlas.materials["cyclist_stripe"]
    m_shorts = atlas.materials["cyclist_shorts"]
    m_helmet = atlas.materials["cyclist_helmet"]
    m_skin = atlas.materials["skin"]
    m_shoe = atlas.materials["shoe"]
    m_tail = atlas.materials["taillight"]

    r_w = 0.38
    z_rear = -0.78
    z_front = 0.98
    add_spoke_wheel(mb, atlas, r_w, z_rear, r_w, 0.064, num_spokes=10)
    add_spoke_wheel(mb, atlas, r_w, z_front, r_w, 0.064, num_spokes=10)

    bb = (0.0, 0.35, -0.20)
    seat_cluster = (0.0, 0.72, -0.38)
    head_top = (0.0, 0.80, 0.68)
    head_bot = (0.0, 0.64, 0.74)

    mb.add_limb(bb, seat_cluster, 0.042, 0.038, m_frame, sides=10)
    mb.add_limb(bb, head_bot, 0.044, 0.042, m_frame, sides=10)
    mb.add_limb(seat_cluster, head_top, 0.040, 0.040, m_frame, sides=10)
    mb.add_limb(head_bot, head_top, 0.044, 0.044, m_frame, sides=10)

    for side in (-1.0, 1.0):
        drop_r = (side * 0.090, r_w, z_rear)
        drop_f = (side * 0.088, r_w, z_front)
        mb.add_limb((side*0.045, 0.35, -0.20), drop_r, 0.032, 0.028, m_frame, sides=8)
        mb.add_limb((side*0.045, 0.70, -0.38), drop_r, 0.030, 0.026, m_frame, sides=8)
        mb.add_limb((side*0.055, 0.64, 0.74), drop_f, 0.034, 0.028, m_frame, sides=8)

    saddle_c = (0.0, 0.80, -0.42)
    mb.add_limb(seat_cluster, saddle_c, 0.030, 0.028, m_trim, sides=8)
    mb.add_ellipsoid((0.0, 0.82, -0.40), 0.125, 0.048, 0.195, m_trim)
    add_outward_box(mb, -0.06, 0.06, 0.73, 0.79, -0.52, -0.44, m_tail)

    stem_top = (0.0, 0.90, 0.66)
    mb.add_limb(head_top, stem_top, 0.034, 0.034, m_trim, sides=8)
    mb.add_limb((-0.44, 0.90, 0.62), (0.44, 0.90, 0.62), 0.032, 0.032, m_chrome, sides=10)
    for side in (-1.0, 1.0):
        mb.add_limb((side*0.30, 0.90, 0.62), (side*0.46, 0.90, 0.60), 0.040, 0.040, m_trim, sides=10)

    hip = (0.0, 0.86, -0.38)
    shoulder_c = (0.0, 1.10, -0.10)
    mb.add_ellipsoid(hip, 0.19, 0.12, 0.16, m_shorts)
    mb.add_limb(hip, shoulder_c, 0.165, 0.20, m_jersey, sides=16, cap1=False)
    mb.add_ellipsoid(shoulder_c, 0.22, 0.095, 0.14, m_jersey)
    mb.add_ellipsoid((0.0, 0.99, -0.25), 0.062, 0.15, 0.075, m_stripe, rot_deg=(42, 0, 0))

    for side in (-1.0, 1.0):
        sh = (side * 0.21, 1.09, -0.09)
        elb = (side * 0.34, 0.99, 0.24)
        hand = (side * 0.38, 0.91, 0.61)
        mb.add_ellipsoid(sh, 0.080, 0.080, 0.080, m_jersey)
        mb.add_limb(sh, elb, 0.066, 0.054, m_jersey, sides=10)
        mb.add_limb(elb, hand, 0.052, 0.044, m_skin, sides=10)
        mb.add_ellipsoid(hand, 0.052, 0.048, 0.058, m_trim)

        hp = (side * 0.135, 0.83, -0.36)
        if side < 0:
            knee = (side * 0.16, 0.70, -0.02)
            foot = (side * 0.14, 0.42, -0.08)
        else:
            knee = (side * 0.16, 0.58, -0.12)
            foot = (side * 0.14, 0.26, -0.22)
        mb.add_limb(hp, knee, 0.088, 0.070, m_shorts, sides=10)
        mb.add_limb(knee, foot, 0.066, 0.050, m_skin, sides=10)
        mb.add_ellipsoid((foot[0], foot[1]-0.01, foot[2]+0.04), 0.060, 0.045, 0.11, m_shoe)

    head_c = (0.0, 1.19, -0.02)
    mb.add_ellipsoid(head_c, 0.138, 0.142, 0.155, m_skin)
    mb.add_ellipsoid((0.0, 1.24, -0.03), 0.162, 0.112, 0.200, m_helmet, rot_deg=(14, 0, 0))
    for vx in (-0.065, 0.0, 0.065):
        mb.add_ellipsoid((vx, 1.27, -0.04), 0.020, 0.090, 0.170, m_trim, rot_deg=(14, 0, 0))
    return mb


def build_walker(atlas: PaletteAtlas, phase: float = 0.0) -> MeshBuilder:
    mb = MeshBuilder(atlas)
    m_skin = atlas.materials["skin"]
    m_hair = atlas.materials["hair"]
    m_hoodie = atlas.materials["hoodie"]
    m_hoodie_d = atlas.materials["hoodie_dark"]
    m_pack = atlas.materials["backpack"]
    m_strap = atlas.materials["backpack_strap"]
    m_pants = atlas.materials["pants"]
    m_shoe = atlas.materials["shoe"]
    m_shoe_acc = atlas.materials["shoe_accent"]

    ang = 2.0 * math.pi * phase
    swing = math.sin(ang)
    bob = abs(math.sin(ang)) * 0.028
    twist = swing * 5.5

    hip_y = 0.72 + bob
    mb.add_ellipsoid((0.0, hip_y, 0.0), 0.185, 0.115, 0.135, m_pants)

    for side, s_mult in ((-1.0, 1.0), (1.0, -1.0)):
        leg_s = swing * s_mult
        hip_pt = (side * 0.108, hip_y - 0.02, 0.0)
        thigh_ang = leg_s * 22.0
        knee_bend = max(0.0, -leg_s) * 20.0 + 5.0
        thigh_vec = rot_x((0.0, -0.33, 0.0), thigh_ang)
        knee_pt = v_add(hip_pt, thigh_vec)
        calf_vec = rot_x((0.0, -0.32, 0.0), thigh_ang - knee_bend)
        ankle_pt = v_add(knee_pt, calf_vec)

        mb.add_limb(hip_pt, knee_pt, 0.088, 0.070, m_pants, sides=12)
        mb.add_ellipsoid(knee_pt, 0.070, 0.070, 0.070, m_pants)
        mb.add_limb(knee_pt, ankle_pt, 0.068, 0.054, m_pants, sides=12)

        foot_c = (ankle_pt[0], max(0.052, ankle_pt[1] - 0.015), ankle_pt[2] + 0.045)
        mb.add_ellipsoid(foot_c, 0.064, 0.050, 0.120, m_shoe)
        mb.add_ellipsoid((foot_c[0], foot_c[1] + 0.016, foot_c[2] - 0.01), 0.058, 0.038, 0.088, m_shoe_acc)

    torso_bot = (0.0, hip_y + 0.02, 0.0)
    torso_top = (0.0, hip_y + 0.40, 0.015)
    mb.add_limb(torso_bot, torso_top, 0.180, 0.215, m_hoodie, sides=16, cap1=False)
    mb.add_ellipsoid((0.0, hip_y + 0.04, 0.0), 0.188, 0.058, 0.142, m_hoodie_d)
    mb.add_ellipsoid(rot_y((0.0, hip_y + 0.39, 0.015), twist), 0.255, 0.095, 0.150, m_hoodie, rot_deg=(0, twist, 0))

    mb.add_ellipsoid(rot_y((0.0, hip_y + 0.45, -0.045), twist), 0.165, 0.075, 0.135, m_hoodie_d, rot_deg=(-14, twist, 0))

    bp_c = rot_y((0.0, hip_y + 0.23, -0.14), twist)
    mb.add_ellipsoid(bp_c, 0.15, 0.175, 0.09, m_pack, rot_deg=(0, twist, 0))
    mb.add_ellipsoid(rot_y((0.0, hip_y + 0.17, -0.195), twist), 0.115, 0.095, 0.048, m_strap, rot_deg=(0, twist, 0))
    mb.add_ellipsoid(rot_y((0.0, hip_y + 0.30, -0.205), twist), 0.055, 0.022, 0.025, m_shoe_acc, rot_deg=(0, twist, 0))
    for side in (-1.0, 1.0):
        s_top = rot_y((side * 0.135, hip_y + 0.42, -0.02), twist)
        s_bot = rot_y((side * 0.145, hip_y + 0.13, -0.10), twist)
        mb.add_limb(s_top, s_bot, 0.030, 0.026, m_strap, sides=8)

    for side, s_mult in ((-1.0, -1.0), (1.0, 1.0)):
        arm_s = swing * s_mult
        sh_pt = rot_y((side * 0.205, hip_y + 0.38, 0.015), twist)
        u_ang = arm_s * 20.0
        f_ang = u_ang + 16.0 + max(0.0, arm_s) * 12.0
        elb_vec = rot_x((side * 0.025, -0.22, 0.0), u_ang)
        elb_pt = v_add(sh_pt, elb_vec)
        hand_vec = rot_x((0.0, -0.20, 0.04), f_ang)
        hand_pt = v_add(elb_pt, hand_vec)

        mb.add_limb(sh_pt, elb_pt, 0.070, 0.058, m_hoodie, sides=12, cap0=False)
        mb.add_ellipsoid(elb_pt, 0.058, 0.058, 0.058, m_hoodie)
        mb.add_limb(elb_pt, hand_pt, 0.056, 0.046, m_hoodie_d, sides=12)
        mb.add_ellipsoid(hand_pt, 0.048, 0.050, 0.054, m_skin)

    neck_pt = (0.0, hip_y + 0.45, 0.025)
    head_pt = (0.0, hip_y + 0.58, 0.035)
    mb.add_limb(neck_pt, head_pt, 0.072, 0.068, m_skin, sides=10)
    mb.add_ellipsoid(head_pt, 0.136, 0.148, 0.146, m_skin)
    for side in (-1.0, 1.0):
        mb.add_ellipsoid((side * 0.136, hip_y + 0.57, 0.025), 0.025, 0.038, 0.028, m_skin)
    mb.add_ellipsoid((0.0, hip_y + 0.625, 0.020), 0.146, 0.132, 0.156, m_hair, rot_deg=(-8, 0, 0))
    mb.add_ellipsoid((0.0, hip_y + 0.675, 0.040), 0.128, 0.088, 0.142, m_hair, rot_deg=(8, 0, 0))
    return mb


# ============================================================================
# Binary glTF 2.0 (.glb) Exporter
# ============================================================================

def export_glb(mb: MeshBuilder, atlas: PaletteAtlas, out_path: Path):
    """Writes a standard self-contained glTF 2.0 binary (.glb) with embedded PNG texture."""
    png_buf = io.BytesIO()
    atlas.img.save(png_buf, format="PNG")
    png_bytes = png_buf.getvalue()
    while len(png_bytes) % 4 != 0:
        png_bytes += b"\x00"

    body_tris = []
    wheel_tris = []
    for i0, i1, i2 in mb.triangles:
        if mb.vertices[i0][8]:
            wheel_tris.append((i0, i1, i2))
        else:
            body_tris.append((i0, i1, i2))

    def pack_submesh(tris):
        remap = {}
        sub_verts = []
        sub_inds = []
        for idx_tri in tris:
            for vi in idx_tri:
                v = mb.vertices[vi]
                key = (
                    round(v[0], 4), round(v[1], 4), round(v[2], 4),
                    round(v[3], 3), round(v[4], 3), round(v[5], 3),
                    round(v[6], 4), round(v[7], 4),
                )
                if key not in remap:
                    remap[key] = len(sub_verts)
                    sub_verts.append(v)
                sub_inds.append(remap[key])
        return sub_verts, sub_inds

    submeshes = [("body", *pack_submesh(body_tris))]
    if wheel_tris:
        submeshes.append(("wheel-set", *pack_submesh(wheel_tris)))

    bin_chunks = [png_bytes]
    buffer_views = [
        {"buffer": 0, "byteOffset": 0, "byteLength": len(png_bytes)}
    ]
    accessors = []
    meshes_json = []
    nodes_json = []
    current_offset = len(png_bytes)

    for mesh_idx, (m_name, s_verts, s_inds) in enumerate(submeshes):
        pos_bytes = bytearray()
        norm_bytes = bytearray()
        uv_bytes = bytearray()
        ind_bytes = bytearray()

        xs = [v[0] for v in s_verts]
        ys = [v[1] for v in s_verts]
        zs = [v[2] for v in s_verts]
        min_pos = [min(xs), min(ys), min(zs)]
        max_pos = [max(xs), max(ys), max(zs)]

        for v in s_verts:
            pos_bytes.extend(struct.pack("<fff", v[0], v[1], v[2]))
            norm_bytes.extend(struct.pack("<fff", v[3], v[4], v[5]))
            uv_bytes.extend(struct.pack("<ff", v[6], v[7]))
        use_u16 = len(s_verts) < 65535
        for idx in s_inds:
            ind_bytes.extend(struct.pack("<H" if use_u16 else "<I", idx))

        def add_bv(b_data):
            nonlocal current_offset
            while len(b_data) % 4 != 0:
                b_data.append(0)
            bv_idx = len(buffer_views)
            buffer_views.append({
                "buffer": 0,
                "byteOffset": current_offset,
                "byteLength": len(b_data),
            })
            bin_chunks.append(bytes(b_data))
            current_offset += len(b_data)
            return bv_idx

        bv_pos = add_bv(pos_bytes)
        bv_norm = add_bv(norm_bytes)
        bv_uv = add_bv(uv_bytes)
        bv_ind = add_bv(ind_bytes)

        acc_pos = len(accessors)
        accessors.append({
            "bufferView": bv_pos,
            "componentType": 5126,
            "count": len(s_verts),
            "type": "VEC3",
            "min": min_pos,
            "max": max_pos,
        })
        acc_norm = len(accessors)
        accessors.append({
            "bufferView": bv_norm,
            "componentType": 5126,
            "count": len(s_verts),
            "type": "VEC3",
        })
        acc_uv = len(accessors)
        accessors.append({
            "bufferView": bv_uv,
            "componentType": 5126,
            "count": len(s_verts),
            "type": "VEC2",
        })
        acc_ind = len(accessors)
        accessors.append({
            "bufferView": bv_ind,
            "componentType": 5123 if use_u16 else 5125,
            "count": len(s_inds),
            "type": "SCALAR",
        })

        meshes_json.append({
            "name": m_name,
            "primitives": [{
                "attributes": {
                    "POSITION": acc_pos,
                    "NORMAL": acc_norm,
                    "TEXCOORD_0": acc_uv,
                },
                "indices": acc_ind,
                "material": 0,
            }],
        })
        nodes_json.append({"name": m_name, "mesh": mesh_idx})

    bin_blob = b"".join(bin_chunks)
    gltf_dict = {
        "asset": {"version": "2.0", "generator": "Waffle 3D Model Generator"},
        "scene": 0,
        "scenes": [{"nodes": list(range(len(nodes_json)))}],
        "nodes": nodes_json,
        "meshes": meshes_json,
        "materials": [{
            "name": "waffle_palette",
            "pbrMetallicRoughness": {
                "baseColorTexture": {"index": 0},
                "metallicFactor": 0.1,
                "roughnessFactor": 0.6,
            },
        }],
        "textures": [{"source": 0}],
        "images": [{"bufferView": 0, "mimeType": "image/png"}],
        "accessors": accessors,
        "bufferViews": buffer_views,
        "buffers": [{"byteLength": len(bin_blob)}],
    }

    json_bytes = json.dumps(gltf_dict, separators=(",", ":")).encode("utf-8")
    while len(json_bytes) % 4 != 0:
        json_bytes += b" "

    total_len = 12 + 8 + len(json_bytes) + 8 + len(bin_blob)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "wb") as fp:
        fp.write(struct.pack("<III", 0x46546C67, 2, total_len))
        fp.write(struct.pack("<II", len(json_bytes), 0x4E4F534A))
        fp.write(json_bytes)
        fp.write(struct.pack("<II", len(bin_blob), 0x004E4942))
        fp.write(bin_blob)


# ============================================================================
# Software 3D Renderer for Baked Walk Frames & Drawable Puck Sprites
# ============================================================================

def render_sprite(
    mb: MeshBuilder,
    atlas: PaletteAtlas,
    model_key: str,
    out_size: int = 256,
    ssaa: int = 3,
    norm_bounds: tuple[float, float, float, float, float, float] | None = None,
) -> Image.Image:
    verts = mb.vertices
    if norm_bounds is not None:
        minX, maxX, minY, maxY, minZ, maxZ = norm_bounds
    else:
        xs = [v[0] for v in verts]; ys = [v[1] for v in verts]; zs = [v[2] for v in verts]
        minX, maxX, minY, maxY, minZ, maxZ = min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)

    is_walker = model_key == "WALKER"
    is_bike = model_key == "BICYCLE"
    is_moto = model_key == "MOTORCYCLE"

    target_len = 2.05 if is_walker else (2.45 if (is_bike or is_moto) else 2.55)
    source_len = (maxY - minY) if is_walker else (maxZ - minZ)
    factor = target_len / max(0.001, source_len)
    cx, cz = (minX + maxX) / 2.0, (minZ + maxZ) / 2.0
    scaled_v = [
        ((v[0] - cx) * factor, (v[1] - minY) * factor, (v[2] - cz) * factor, v[3], v[4], v[5], v[6], v[7])
        for v in verts
    ]

    W = H = out_size * ssaa
    base = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sh_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sh_layer)
    rx = int(W * (0.14 if (is_bike or is_moto or is_walker) else 0.22))
    ry = int(H * (0.15 if is_walker else 0.27))
    cy_off = int(H * (0.04 if is_walker else 0.02))
    sd.ellipse((W // 2 - rx, H // 2 - ry + cy_off, W // 2 + rx, H // 2 + ry + cy_off), fill=(0, 0, 0, 115))
    sh_layer = sh_layer.filter(ImageFilter.GaussianBlur(int(9 * ssaa)))
    base.alpha_composite(sh_layer)

    pix = base.load()
    depth = [[1e9] * W for _ in range(H)]

    eye = (0.0, 4.5, -5.0)
    target = (
        (0.0, 0.78, -0.05)
        if is_walker
        else ((0.0, 0.56, -0.14) if (is_bike or is_moto) else (0.0, 0.40, -0.22))
    )
    fw = v_norm(v_sub(target, eye))
    rt = v_norm(v_cross((0.0, 1.0, 0.0), fw))
    up = v_cross(fw, rt)
    focal = 1.0 / math.tan(math.radians(15.0))
    scale = W * 0.50
    light = v_norm((-0.38, 0.84, -0.38))
    half_v = v_norm((-0.22, 0.78, -0.58))
    tex = atlas.img; tpix = tex.load(); tw, th = tex.width, tex.height

    def project(v):
        rel = v_sub((v[0], v[1], v[2]), eye)
        d = v_dot(rel, fw)
        if d <= 0.05:
            return None
        return (W / 2.0 + (v_dot(rel, rt) * focal / d) * scale, H / 2.0 - (v_dot(rel, up) * focal / d) * scale, d)

    proj_v = [project(v) for v in scaled_v]
    for i0, i1, i2 in mb.triangles:
        pa, pb, pc = proj_v[i0], proj_v[i1], proj_v[i2]
        if not pa or not pb or not pc:
            continue
        a, b, c = scaled_v[i0], scaled_v[i1], scaled_v[i2]
        x0, x1 = max(0, int(min(pa[0], pb[0], pc[0]))), min(W - 1, int(max(pa[0], pb[0], pc[0])))
        y0, y1 = max(0, int(min(pa[1], pb[1], pc[1]))), min(H - 1, int(max(pa[1], pb[1], pc[1])))
        denom = (pb[1] - pc[1]) * (pa[0] - pc[0]) + (pc[0] - pb[0]) * (pa[1] - pc[1])
        if abs(denom) < 1e-5:
            continue
        for py in range(y0, y1 + 1):
            for px in range(x0, x1 + 1):
                w0 = ((pb[1] - pc[1]) * (px - pc[0]) + (pc[0] - pb[0]) * (py - pc[1])) / denom
                w1 = ((pc[1] - pa[1]) * (px - pc[0]) + (pa[0] - pc[0]) * (py - pc[1])) / denom
                w2 = 1.0 - w0 - w1
                if w0 < -0.001 or w1 < -0.001 or w2 < -0.001:
                    continue
                z = w0 * pa[2] + w1 * pb[2] + w2 * pc[2]
                if z >= depth[py][px]:
                    continue
                u = w0 * a[6] + w1 * b[6] + w2 * c[6]
                v = w0 * a[7] + w1 * b[7] + w2 * c[7]
                rgba = tpix[
                    max(0, min(tw - 1, int((u - math.floor(u)) * (tw - 1)))),
                    max(0, min(th - 1, int((v - math.floor(v)) * (th - 1)))),
                ]
                nrm = v_norm((
                    w0 * a[3] + w1 * b[3] + w2 * c[3],
                    w0 * a[4] + w1 * b[4] + w2 * c[4],
                    w0 * a[5] + w1 * b[5] + w2 * c[5],
                ))
                sh = (0.54 + 0.16 * nrm[1]) + 0.36 * max(0.0, v_dot(nrm, light))
                spec = (max(0.0, v_dot(nrm, half_v)) ** 14) * 0.16
                pix[px, py] = (
                    min(255, int(rgba[0] * sh + 255 * spec)),
                    min(255, int(rgba[1] * sh + 255 * spec)),
                    min(255, int(rgba[2] * sh + 255 * spec)),
                    255,
                )
                depth[py][px] = z

    return base.resize((out_size, out_size), Image.Resampling.LANCZOS)


def render_arrow_sprite(out_size: int = 256) -> Image.Image:
    W = out_size * 2
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    sh = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse((int(W*0.20), int(W*0.30), int(W*0.80), int(W*0.84)), fill=(0, 0, 0, 115))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(20)))
    d = ImageDraw.Draw(img)
    cx = W // 2
    depth_poly = [(cx, int(W*0.18)), (int(W*0.80), int(W*0.80)), (cx, int(W*0.64)), (int(W*0.20), int(W*0.80))]
    top_poly   = [(cx, int(W*0.12)), (int(W*0.78), int(W*0.74)), (cx, int(W*0.58)), (int(W*0.22), int(W*0.74))]
    left_bevel = [top_poly[0], top_poly[2], top_poly[3]]
    right_bevel = [top_poly[0], top_poly[1], top_poly[2]]
    d.polygon(depth_poly, fill=(148, 78, 12, 255))
    d.polygon(left_bevel, fill=(252, 184, 74, 255))
    d.polygon(right_bevel, fill=(228, 142, 38, 255))
    d.line(top_poly + [top_poly[0]], fill=(255, 255, 255, 255), width=10, joint="curve")
    return img.resize((out_size, out_size), Image.Resampling.LANCZOS)


def render_dot_sprite(out_size: int = 128) -> Image.Image:
    img = Image.new("RGBA", (out_size, out_size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse((22, 72, 106, 116), fill=(0, 0, 0, 110))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(8)))
    d.ellipse((24, 24, 104, 104), fill=(255, 255, 255, 255), outline=(27, 36, 48, 255), width=4)
    d.ellipse((34, 34, 94, 94), fill=(30, 136, 229, 255), outline=(12, 82, 150, 255), width=3)
    d.ellipse((46, 42, 72, 64), fill=(170, 220, 255, 180))
    return img


def main():
    CARS_DIR.mkdir(parents=True, exist_ok=True)
    WALKER_DIR.mkdir(parents=True, exist_ok=True)
    DRAWABLE_DIR.mkdir(parents=True, exist_ok=True)

    atlas = PaletteAtlas()
    setup_common_palette(atlas)
    atlas.img.save(CARS_DIR / "colormap.png")
    print("Wrote", CARS_DIR / "colormap.png")

    sedan_mb = build_sedan(atlas)
    suv_mb = build_suv(atlas)
    pickup_mb = build_pickup(atlas)
    moto_mb = build_motorcycle(atlas)
    bike_mb = build_bicycle(atlas)
    walker0_mb = build_walker(atlas, phase=0.0)

    glb_targets = [
        ("sedan-sports.glb", sedan_mb),
        ("suv-luxury.glb", suv_mb),
        ("truck-flat.glb", pickup_mb),
        ("motorcycle.glb", moto_mb),
        ("bicycle.glb", bike_mb),
        ("walker.glb", walker0_mb),
    ]
    for filename, mb in glb_targets:
        out_p = CARS_DIR / filename
        export_glb(mb, atlas, out_p)
        print(f"Wrote {out_p} ({len(mb.vertices)} verts, {len(mb.triangles)} tris, {out_p.stat().st_size} bytes)")

    # Fixed normalization bounds across all 12 walker frames so the character never jitters in scale/center
    w_xs = [v[0] for v in walker0_mb.vertices]
    w_ys = [v[1] for v in walker0_mb.vertices]
    w_zs = [v[2] for v in walker0_mb.vertices]
    walker_bounds = (min(w_xs), max(w_xs), 0.0, max(w_ys), min(w_zs), max(w_zs))

    for frame_idx in range(12):
        phase = frame_idx / 12.0
        w_mb = build_walker(atlas, phase=phase)
        frame_img = render_sprite(w_mb, atlas, "WALKER", out_size=256, ssaa=3, norm_bounds=walker_bounds)
        frame_path = WALKER_DIR / f"frame_{frame_idx:02d}.png"
        frame_img.save(frame_path)
    print("Wrote 12 walk cycle frames to", WALKER_DIR)

    # Render high-res 3D puck sprites for VehicleIconPicker & fallback drawables
    puck_targets = [
        ("ic_puck_sedan_3d.png", sedan_mb, "SEDAN"),
        ("ic_puck_suv_3d.png", suv_mb, "SUV"),
        ("ic_puck_pickup_3d.png", pickup_mb, "PICKUP"),
        ("ic_puck_bike_3d.png", bike_mb, "BICYCLE"),
        ("ic_puck_walk_3d.png", build_walker(atlas, phase=0.12), "WALKER"),
    ]
    for filename, mb, key in puck_targets:
        bounds = walker_bounds if key == "WALKER" else None
        im = render_sprite(mb, atlas, key, out_size=256, ssaa=3, norm_bounds=bounds)
        im.save(DRAWABLE_DIR / filename)
        print("Wrote", DRAWABLE_DIR / filename)

    render_arrow_sprite(256).save(DRAWABLE_DIR / "ic_puck_arrow_3d.png")
    render_dot_sprite(128).save(DRAWABLE_DIR / "ic_puck_dot.png")
    print("Wrote arrow and dot pucks to", DRAWABLE_DIR)


if __name__ == "__main__":
    main()
