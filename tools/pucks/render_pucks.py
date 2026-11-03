#!/usr/bin/env python3
"""Render the navigation vehicle sprites with a raised, perspective 3D look.

MapLibre has no 3D vehicle renderer, so the puck is a billboard sprite. The old sprites were
top-down decals. These draw a raised hood/roof, dark side panels, glass, wheels and a soft
ground shadow so the car reads as a 3D object at the navigation camera tilt.

Output: app/src/main/res/drawable-nodpi/ic_puck_{sedan,suv,pickup}_3d.png
"""

from __future__ import annotations

import os
from PIL import Image, ImageDraw, ImageFilter

SIZE = 1024
OUT = 256
OUT_DIR = os.path.join("app", "src", "main", "res", "drawable-nodpi")


def mix(a, b, t):
    return tuple(round(a[i] * (1 - t) + b[i] * t) for i in range(3))


def rgba(color, alpha=255):
    return (*color, alpha)


def draw_car(model: str) -> Image.Image:
    if model == "sedan":
        body = (211, 76, 45)
        light = (247, 128, 88)
        dark = (115, 35, 27)
        glass = (22, 42, 55)
        glass_light = (67, 116, 143)
    elif model == "suv":
        body = (230, 171, 35)
        light = (255, 218, 92)
        dark = (133, 88, 16)
        glass = (25, 43, 54)
        glass_light = (80, 125, 148)
    else:
        body = (38, 120, 169)
        light = (103, 181, 221)
        dark = (18, 64, 98)
        glass = (20, 39, 51)
        glass_light = (75, 120, 144)

    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    # Soft ground shadow.
    shadow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.ellipse((235, 690, 789, 885), fill=(0, 0, 0, 115))
    shadow = shadow.filter(ImageFilter.GaussianBlur(32))
    image.alpha_composite(shadow)

    # Wheels first so the body overlaps their top edge.
    def wheel(x, y, w, h):
        draw.rounded_rectangle(
            (x, y, x + w, y + h), radius=30, fill=(18, 22, 26), outline=(7, 9, 11), width=8
        )
        draw.rounded_rectangle(
            (x + 12, y + 12, x + w - 12, y + h - 12),
            radius=20,
            fill=(55, 62, 68),
        )
        draw.ellipse((x + 24, y + 28, x + w - 24, y + h - 28), fill=(130, 138, 144))

    if model == "sedan":
        wheel(190, 585, 74, 145)
        wheel(760, 585, 74, 145)
        wheel(225, 250, 64, 125)
        wheel(735, 250, 64, 125)
        rear_y, front_y = 765, 205
        rear_half, front_half = 270, 190
        roof_y = 470
    elif model == "suv":
        wheel(175, 570, 84, 160)
        wheel(765, 570, 84, 160)
        wheel(220, 235, 72, 140)
        wheel(732, 235, 72, 140)
        rear_y, front_y = 780, 190
        rear_half, front_half = 290, 205
        roof_y = 445
    else:
        wheel(180, 580, 80, 150)
        wheel(764, 580, 80, 150)
        wheel(220, 245, 68, 132)
        wheel(736, 245, 68, 132)
        rear_y, front_y = 780, 200
        rear_half, front_half = 280, 198
        roof_y = 450

    cx = SIZE // 2
    # Lower body / side panels.
    body_poly = [
        (cx - rear_half, rear_y),
        (cx + rear_half, rear_y),
        (cx + front_half, front_y),
        (cx - front_half, front_y),
    ]
    draw.polygon(body_poly, fill=rgba(dark), outline=rgba((8, 12, 16)), width=8)

    # Main body top surface, inset.
    top_inset = 22
    top_poly = [
        (cx - rear_half + top_inset, rear_y - 18),
        (cx + rear_half - top_inset, rear_y - 18),
        (cx + front_half - 12, front_y + 26),
        (cx - front_half + 12, front_y + 26),
    ]
    draw.polygon(top_poly, fill=rgba(body))

    # Hood.
    hood = [
        (cx - front_half + 18, front_y + 28),
        (cx + front_half - 18, front_y + 28),
        (cx + front_half - 44, 365),
        (cx - front_half + 44, 365),
    ]
    draw.polygon(hood, fill=rgba(mix(body, light, 0.18)))
    draw.line(hood + [hood[0]], fill=rgba(dark), width=5)

    # Windshield.
    windshield = [
        (cx - front_half + 52, 365),
        (cx + front_half - 52, 365),
        (cx + 195, 475),
        (cx - 195, 475),
    ]
    draw.polygon(windshield, fill=rgba(glass), outline=rgba((7, 11, 15)), width=6)
    draw.line((cx - 150, 385, cx + 150, 385), fill=rgba(glass_light), width=5)
    draw.line((cx - 130, 410, cx + 95, 410), fill=rgba(glass_light, 150), width=3)

    # Roof / cabin top.
    roof = [
        (cx - 195, 475),
        (cx + 195, 475),
        (cx + 210, roof_y + 170),
        (cx - 210, roof_y + 170),
    ]
    draw.polygon(roof, fill=rgba(mix(body, light, 0.28)), outline=rgba(dark), width=6)
    draw.rounded_rectangle(
        (cx - 125, 505, cx + 125, roof_y + 120), radius=28, fill=rgba(mix(body, light, 0.38))
    )
    draw.line((cx - 175, 500, cx + 175, 500), fill=rgba(light), width=6)

    if model == "pickup":
        # Cargo bed instead of a rear window.
        bed = [
            (cx - 205, roof_y + 170),
            (cx + 205, roof_y + 170),
            (cx + 235, rear_y - 40),
            (cx - 235, rear_y - 40),
        ]
        draw.polygon(bed, fill=rgba(dark), outline=rgba((8, 12, 16)), width=6)
        draw.line((cx - 190, roof_y + 190, cx - 190, rear_y - 60), fill=rgba(mix(dark, body, 0.4)), width=5)
        draw.line((cx + 190, roof_y + 190, cx + 190, rear_y - 60), fill=rgba(mix(dark, body, 0.4)), width=5)
    else:
        # Rear window.
        rear_window = [
            (cx - 205, roof_y + 170),
            (cx + 205, roof_y + 170),
            (cx + 230, rear_y - 55),
            (cx - 230, rear_y - 55),
        ]
        draw.polygon(rear_window, fill=rgba(glass), outline=rgba((7, 11, 15)), width=6)
        draw.line((cx - 165, roof_y + 190, cx + 165, roof_y + 190), fill=rgba(glass_light, 150), width=4)

    # Side windows.
    draw.polygon(
        [(cx - 238, 485), (cx - 205, 485), (cx - 212, 645), (cx - 245, 645)],
        fill=rgba(glass),
        outline=rgba((7, 11, 15)),
        width=4,
    )
    draw.polygon(
        [(cx + 205, 485), (cx + 238, 485), (cx + 245, 645), (cx + 212, 645)],
        fill=rgba(glass),
        outline=rgba((7, 11, 15)),
        width=4,
    )

    # Headlights and grille.
    draw.rounded_rectangle((cx - 172, front_y + 36, cx - 55, front_y + 78), radius=12, fill=(255, 244, 196))
    draw.rounded_rectangle((cx + 55, front_y + 36, cx + 172, front_y + 78), radius=12, fill=(255, 244, 196))
    draw.rounded_rectangle((cx - 90, front_y + 88, cx + 90, front_y + 128), radius=10, fill=(22, 27, 31))

    # Taillights.
    draw.rounded_rectangle((cx - 235, rear_y - 82, cx - 120, rear_y - 38), radius=10, fill=(225, 38, 38))
    draw.rounded_rectangle((cx + 120, rear_y - 82, cx + 235, rear_y - 38), radius=10, fill=(225, 38, 38))

    # 3D edge highlight on the left flank and roof.
    draw.line((cx - rear_half + 18, rear_y - 22, cx - front_half + 10, front_y + 30), fill=rgba(light, 190), width=9)
    draw.line((cx - 210, roof_y + 178, cx - 225, rear_y - 58), fill=rgba(light, 120), width=5)
    draw.line((cx + 210, roof_y + 178, cx + 225, rear_y - 58), fill=rgba((0, 0, 0), 90), width=5)

    # Thin body outline for contrast on light and dark maps.
    draw.line(body_poly + [body_poly[0]], fill=rgba((8, 12, 16)), width=8)

    # Downsample with LANCZOS for clean anti-aliasing.
    return image.resize((OUT, OUT), Image.LANCZOS)


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    for model in ("sedan", "suv", "pickup"):
        path = os.path.join(OUT_DIR, f"ic_puck_{model}_3d.png")
        draw_car(model).save(path, "PNG")
        print("wrote", path)


if __name__ == "__main__":
    main()
