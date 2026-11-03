"""Generates the 3D navigation pucks as transparent PNGs.

- sedan / SUV / pickup: a 3/4 rear-right isometric car
- bike: a 3/4 isometric bicycle
- walk: a 3/4 low-poly walker
- arrow: the classic navigation arrow

Run: python tools/puck/generate_pucks.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import math

OUT = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res" / "drawable-nodpi"
SIZE = 512
S = 150.0
CX, CY = 256.0, 300.0

INK = (27, 36, 48, 255)
WHITE = (255, 255, 255, 255)
HONEY = (242, 163, 60, 255)


def shade(color, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in color[:3]) + (255,)


def proj(x, y, z):
    # 3/4 rear-right view: forward (+y) goes up-right, right (+x) goes right-down.
    return (
        CX + (x * 0.96 + y * 0.42) * S,
        CY - (y * 0.78 - x * 0.20 + z) * S,
    )


def poly(draw, pts3, fill, outline=None, width=3):
    pts = [proj(*p) for p in pts3]
    draw.polygon(pts, fill=fill)
    if outline:
        draw.line(pts + [pts[0]], fill=outline, width=width, joint="curve")


def gradient_poly(img, pts3, top, bottom):
    pts = [proj(*p) for p in pts3]
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    x0, x1 = int(min(xs)) - 2, int(max(xs)) + 2
    y0, y1 = int(min(ys)) - 2, int(max(ys)) + 2
    w, h = max(1, x1 - x0), max(1, y1 - y0)
    grad = Image.new("RGBA", (1, h), (0, 0, 0, 0))
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(4))
        ImageDraw.Draw(grad).point((0, y), fill=c)
    grad = grad.resize((w, h))
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).polygon([(x - x0, y - y0) for x, y in pts], fill=255)
    img.paste(grad, (x0, y0), mask)


def wheel(draw, x, y, z, r, tire=(17, 22, 29, 255)):
    # circle in the x = const side plane
    pts = []
    for i in range(40):
        a = 2 * math.pi * i / 40
        pts.append((x, y + math.cos(a) * r, z + math.sin(a) * r))
    p = [proj(*q) for q in pts]
    draw.polygon(p, fill=tire)
    draw.line(p + [p[0]], fill=(8, 11, 15, 255), width=4, joint="curve")
    inner = []
    for i in range(32):
        a = 2 * math.pi * i / 32
        inner.append((x - 0.001, y + math.cos(a) * r * 0.58, z + math.sin(a) * r * 0.58))
    q = [proj(*v) for v in inner]
    draw.polygon(q, fill=(128, 142, 158, 255))
    draw.line(q + [q[0]], fill=(64, 74, 88, 255), width=3, joint="curve")


def car(kind, body):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    if kind == "sedan":
        length, width, body_h, cabin_h = 2.75, 1.12, 0.46, 0.38
        yb0, yb1, yt0, yt1 = -0.55, 0.40, -0.35, 0.20
    elif kind == "suv":
        length, width, body_h, cabin_h = 2.80, 1.18, 0.60, 0.52
        yb0, yb1, yt0, yt1 = -0.72, 0.55, -0.52, 0.35
    else:  # pickup
        length, width, body_h, cabin_h = 2.90, 1.18, 0.55, 0.46
        yb0, yb1, yt0, yt1 = 0.10, 0.98, 0.26, 0.82

    l2, w2 = length / 2, width / 2
    cx0b, cx1b = -w2 + 0.06, w2 - 0.06
    cx0t, cx1t = -w2 + 0.16, w2 - 0.16
    top = body_h + cabin_h

    shadow_pts = [proj(-w2, -l2, 0), proj(w2, -l2, 0), proj(w2, l2, 0), proj(-w2, l2, 0)]
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).polygon([(x + 8, y + 18) for x, y in shadow_pts], fill=(0, 0, 0, 120))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(16)))

    poly(
        d,
        [(-w2, -l2, 0), (w2, -l2, 0), (w2, -l2, body_h), (-w2, -l2, body_h)],
        shade(body, 0.55),
        INK,
        4,
    )
    gradient_poly(
        img,
        [(w2, -l2, 0), (w2, l2, 0), (w2, l2, body_h), (w2, -l2, body_h)],
        shade(body, 1.18),
        shade(body, 0.62),
    )
    poly(
        d,
        [(w2, -l2, 0), (w2, l2, 0), (w2, l2, body_h), (w2, -l2, body_h)],
        None,
        INK,
        4,
    )
    poly(
        d,
        [(-w2, -l2, body_h), (w2, -l2, body_h), (w2, l2, body_h), (-w2, l2, body_h)],
        shade(body, 1.20),
        INK,
        4,
    )

    poly(
        d,
        [(cx0b, yb0, body_h), (cx1b, yb0, body_h), (cx1t, yt0, top), (cx0t, yt0, top)],
        shade(body, 0.48),
        INK,
        4,
    )
    gradient_poly(
        img,
        [(cx1b, yb0, body_h), (cx1b, yb1, body_h), (cx1t, yt1, top), (cx1t, yt0, top)],
        shade(body, 1.16),
        shade(body, 0.56),
    )
    poly(
        d,
        [(cx1b, yb0, body_h), (cx1b, yb1, body_h), (cx1t, yt1, top), (cx1t, yt0, top)],
        None,
        INK,
        4,
    )
    poly(
        d,
        [(cx0t, yt0, top), (cx1t, yt0, top), (cx1t, yt1, top), (cx0t, yt1, top)],
        shade(body, 1.30),
        INK,
        4,
    )

    poly(
        d,
        [(cx0b + 0.08, yb0 + 0.005, body_h + 0.06),
         (cx1b - 0.08, yb0 + 0.005, body_h + 0.06),
         (cx1t - 0.06, yt0 + 0.005, top - 0.06),
         (cx0t + 0.06, yt0 + 0.005, top - 0.06)],
        (26, 42, 60, 255),
        (10, 15, 22, 255),
        3,
    )

    for y0, y1 in [(yb0 + 0.10, yb0 + 0.40), (yb0 + 0.48, yb1 - 0.08)]:
        poly(
            d,
            [(cx1b - 0.004, y0, body_h + 0.06),
             (cx1b - 0.004, y1, body_h + 0.06),
             (cx1t - 0.004, y1 - 0.05, top - 0.06),
             (cx1t - 0.004, y0 + 0.05, top - 0.06)],
            (30, 52, 74, 255),
            (10, 15, 22, 255),
            3,
        )
    d.line(
        [proj(cx1t - 0.004, yt0 + 0.08, top - 0.12),
         proj(cx1t - 0.004, yt1 - 0.10, top - 0.12)],
        fill=(180, 220, 240, 120),
        width=4,
    )

    wheel(d, w2 + 0.06, -l2 + 0.50, 0.23, 0.25)
    wheel(d, w2 + 0.06, l2 - 0.50, 0.23, 0.25)

    poly(
        d,
        [(-w2 + 0.02, -l2 - 0.006, 0.02),
         (w2 - 0.02, -l2 - 0.006, 0.02),
         (w2 - 0.02, -l2 - 0.006, 0.14),
         (-w2 + 0.02, -l2 - 0.006, 0.14)],
        (38, 46, 56, 255),
        (12, 16, 22, 255),
        2,
    )
    for x0, x1 in [(-w2 + 0.08, -w2 + 0.40), (w2 - 0.40, w2 - 0.08)]:
        poly(
            d,
            [(x0, -l2 - 0.007, body_h * 0.50),
             (x1, -l2 - 0.007, body_h * 0.50),
             (x1, -l2 - 0.007, body_h * 0.76),
             (x0, -l2 - 0.007, body_h * 0.76)],
            (229, 57, 64, 255),
            (80, 10, 14, 255),
            3,
        )
    poly(
        d,
        [(-0.16, -l2 - 0.009, 0.17),
         (0.16, -l2 - 0.009, 0.17),
         (0.16, -l2 - 0.009, 0.28),
         (-0.16, -l2 - 0.009, 0.28)],
        (235, 238, 240, 255),
        (40, 48, 58, 255),
        2,
    )

    d.line(
        [proj(w2 + 0.002, yb0 - 0.06, 0.06),
         proj(w2 + 0.002, yb0 - 0.06, body_h * 0.88)],
        fill=(40, 48, 58, 120),
        width=3,
    )
    d.line(
        [proj(w2 + 0.002, -l2 + 0.10, body_h * 0.42),
         proj(w2 + 0.002, l2 - 0.10, body_h * 0.42)],
        fill=(255, 255, 255, 70),
        width=4,
    )

    if kind == "pickup":
        by0, by1 = -l2 + 0.06, yb0 - 0.05
        poly(
            d,
            [(-w2 + 0.08, by0, body_h + 0.01),
             (w2 - 0.08, by0, body_h + 0.01),
             (w2 - 0.08, by1, body_h + 0.01),
             (-w2 + 0.08, by1, body_h + 0.01)],
            (24, 32, 40, 255),
            INK,
            3,
        )
        poly(
            d,
            [(w2 - 0.08, by0, body_h), (w2 - 0.08, by1, body_h),
             (w2 - 0.08, by1, body_h + 0.16), (w2 - 0.08, by0, body_h + 0.16)],
            shade(body, 0.90),
            INK,
            3,
        )

    return img


def arrow_badge(mode):
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse((40, 188, 216, 238), fill=(0, 0, 0, 110))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(10)))
    depth = [(128, 42), (214, 208), (128, 168), (42, 208)]
    top = [(128, 16), (210, 190), (128, 150), (46, 190)]
    d.polygon(depth, fill=(178, 96, 16, 255), outline=INK)
    d.polygon(top, fill=HONEY, outline=WHITE, width=6)
    d.line([(128, 28), (128, 146)], fill=(255, 255, 255, 60), width=6)
    d.ellipse((70, 116, 186, 232), fill=(0, 0, 0, 70))
    d.ellipse((72, 110, 184, 222), fill=WHITE, outline=INK, width=7)
    if mode == "bike":
        for cx in (104, 152):
            d.ellipse((cx - 16, 148, cx + 16, 180), outline=INK, width=6)
        d.line([(104, 164), (128, 142), (152, 164), (104, 164)], fill=HONEY, width=5, joint="curve")
        d.line([(128, 142), (128, 170)], fill=HONEY, width=5)
        d.line([(118, 138), (138, 138)], fill=INK, width=6)
        d.line([(120, 170), (136, 170)], fill=INK, width=6)
    else:
        d.ellipse((118, 128, 138, 148), fill=INK)
        d.line([(128, 148), (128, 170)], fill=INK, width=7)
        d.line([(112, 156), (144, 156)], fill=INK, width=6)
        d.line([(128, 170), (114, 188)], fill=INK, width=6)
        d.line([(128, 170), (142, 188)], fill=INK, width=6)
    return img


def arrow():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse((50, 180, 206, 240), fill=(0, 0, 0, 110))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(10)))
    depth = [(128, 42), (214, 208), (128, 168), (42, 208)]
    top = [(128, 16), (210, 190), (128, 150), (46, 190)]
    d.polygon(depth, fill=(178, 96, 16, 255))
    d.polygon(top, fill=HONEY, outline=WHITE, width=6)
    d.line([(128, 28), (128, 146)], fill=(255, 255, 255, 60), width=6)
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    car("sedan", (226, 82, 54)).resize((256, 256), Image.Resampling.LANCZOS).save(
        OUT / "ic_puck_sedan_3d.png"
    )
    car("suv", (244, 191, 56)).resize((256, 256), Image.Resampling.LANCZOS).save(
        OUT / "ic_puck_suv_3d.png"
    )
    car("pickup", (72, 158, 92)).resize((256, 256), Image.Resampling.LANCZOS).save(
        OUT / "ic_puck_pickup_3d.png"
    )
    arrow_badge("bike").save(OUT / "ic_puck_bike_3d.png")
    arrow_badge("walk").save(OUT / "ic_puck_walk_3d.png")
    arrow().save(OUT / "ic_puck_arrow_3d.png")
    print("wrote pucks to", OUT)


if __name__ == "__main__":
    main()
