"""Generates the navigation pucks as transparent PNGs.

- sedan / SUV / pickup: a top-down 3D car with the nose pointing up, so `iconRotate` can align it
  with the GPS bearing without the car looking sideways.
- bike: a top-down 3D bicycle with a rider
- walk: a top-down 3D walker
- arrow: the classic navigation arrow
- dot: the neutral location dot used outside active navigation

Run: python tools/puck/generate_pucks.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import math

OUT = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res" / "drawable-nodpi"

INK = (27, 36, 48, 255)
WHITE = (255, 255, 255, 255)
HONEY = (242, 163, 60, 255)
GLASS = (23, 43, 66, 255)
GLASS_HI = (92, 150, 190, 120)
TIRE = (17, 22, 29, 255)
HUB = (128, 142, 158, 255)


def shade(color, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in color[:3]) + (255,)


def vertical_gradient(width, height, top, bottom):
    grad = Image.new("RGBA", (1, height), (0, 0, 0, 0))
    for y in range(height):
        t = y / max(1, height - 1)
        c = tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(4))
        ImageDraw.Draw(grad).point((0, y), fill=c)
    return grad.resize((width, height))


def gradient_round_rect(img, box, radius, top, bottom, outline=None, width=3):
    x0, y0, x1, y1 = [int(v) for v in box]
    w, h = max(1, x1 - x0), max(1, y1 - y0)
    grad = vertical_gradient(w, h, top, bottom)
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((x0, y0, x1, y1), radius, fill=255)
    img.paste(grad, (x0, y0), mask.crop((x0, y0, x1, y1)))
    if outline:
        ImageDraw.Draw(img).rounded_rectangle(
            (x0, y0, x1, y1), radius, outline=outline, width=width
        )


def shadow(img, box, radius, offset=(10, 20), blur=18, alpha=120):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    x0, y0, x1, y1 = box
    ImageDraw.Draw(layer).rounded_rectangle(
        (x0 + offset[0], y0 + offset[1], x1 + offset[0], y1 + offset[1]),
        radius,
        fill=(0, 0, 0, alpha),
    )
    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(blur)))


def wheel(d, box):
    x0, y0, x1, y1 = box
    d.rounded_rectangle(box, radius=(x1 - x0) // 3, fill=TIRE, outline=INK, width=4)
    inset = 7
    d.rounded_rectangle(
        (x0 + inset, y0 + inset, x1 - inset, y1 - inset),
        radius=(x1 - x0 - inset * 2) // 3,
        fill=HUB,
        outline=(64, 74, 88, 255),
        width=3,
    )
    d.line([(x0 + inset + 4, y0 + inset + 4), (x1 - inset - 4, y1 - inset - 4)], fill=(190, 202, 216, 130), width=3)


def topdown_car(kind, body):
    img = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    if kind == "sedan":
        left, right = 181, 331
        top, bottom = 106, 406
        roof = (212, 250, 300, 335)
        windshield = [(205, 205), (307, 205), (302, 252), (210, 252)]
        rear_window = [(210, 335), (302, 335), (307, 372), (205, 372)]
        front_axle, rear_axle = (145, 195), (315, 365)
    elif kind == "suv":
        left, right = 173, 339
        top, bottom = 100, 412
        roof = (205, 238, 307, 348)
        windshield = [(198, 198), (314, 198), (307, 242), (205, 242)]
        rear_window = [(205, 348), (307, 348), (314, 384), (198, 384)]
        front_axle, rear_axle = (142, 198), (318, 374)
    else:  # pickup
        left, right = 173, 339
        top, bottom = 100, 420
        roof = (210, 232, 302, 300)
        windshield = [(202, 190), (310, 190), (303, 236), (209, 236)]
        rear_window = [(209, 300), (303, 300), (308, 320), (204, 320)]
        front_axle, rear_axle = (145, 198), (330, 386)

    radius = 34
    shadow(img, (left, top, right, bottom), radius)

    # Wheels sit under the body and poke out at the corners.
    for y0, y1 in (front_axle, rear_axle):
        wheel(d, (left - 13, y0, left + 18, y1))
        wheel(d, (right - 18, y0, right + 13, y1))

    # Lower body / sidewall.
    d.rounded_rectangle(
        (left, top, right, bottom), radius=radius, fill=shade(body, 0.62), outline=INK, width=4
    )

    # Top body surface with a soft front-to-rear gradient.
    gradient_round_rect(
        img,
        (left + 8, top + 6, right - 8, bottom - 6),
        radius - 5,
        shade(body, 1.16),
        shade(body, 0.82),
        outline=INK,
        width=3,
    )

    # Left highlight and right shadow bevels.
    d.line(
        [(left + 13, top + 30), (left + 13, bottom - 30)],
        fill=(255, 255, 255, 65),
        width=5,
    )
    d.line(
        [(right - 13, top + 30), (right - 13, bottom - 30)],
        fill=(0, 0, 0, 70),
        width=6,
    )

    if kind == "pickup":
        # Open cargo bed with a dark floor and body-coloured walls.
        bed_top = 300
        d.rounded_rectangle(
            (left + 14, bed_top, right - 14, bottom - 14),
            radius=12,
            fill=(35, 45, 56, 255),
            outline=INK,
            width=3,
        )
        d.rounded_rectangle(
            (left + 24, bed_top + 10, right - 24, bottom - 26),
            radius=8,
            fill=(24, 32, 42, 255),
            outline=(10, 15, 22, 255),
            width=2,
        )
        for y in range(bed_top + 24, bottom - 30, 26):
            d.line([(left + 28, y), (right - 28, y)], fill=(58, 70, 84, 180), width=2)
        # Tailgate.
        d.rounded_rectangle(
            (left + 14, bottom - 24, right - 14, bottom - 10),
            radius=8,
            fill=shade(body, 0.95),
            outline=INK,
            width=3,
        )

    # Hood.
    hood_top = top + 22
    hood_bottom = windshield[0][1] - 4
    hood = [
        (left + 18, hood_top),
        (right - 18, hood_top),
        (right - 24, hood_bottom),
        (left + 24, hood_bottom),
    ]
    d.polygon(hood, fill=shade(body, 1.20), outline=INK)
    d.line([(left + 24, hood_bottom), (right - 24, hood_bottom)], fill=INK, width=3)
    d.line([(256, hood_top + 8), (256, hood_bottom - 6)], fill=shade(body, 0.78), width=3)
    for x in (left + 48, right - 48):
        d.line([(x, hood_top + 12), (x, hood_bottom - 10)], fill=shade(body, 0.88), width=3)

    # Windshield.
    d.polygon(windshield, fill=GLASS, outline=INK)
    d.line(
        [windshield[0], windshield[1]],
        fill=GLASS_HI,
        width=5,
    )
    d.line(
        [(windshield[0][0] + 10, windshield[0][1] + 10),
         (windshield[1][0] - 10, windshield[1][1] + 10)],
        fill=(150, 200, 230, 70),
        width=4,
    )

    # Side windows and roof.
    roof_left, roof_top, roof_right, roof_bottom = roof
    for x0, x1 in ((left + 18, roof_left - 1), (roof_right + 1, right - 18)):
        d.rounded_rectangle(
            (x0, roof_top + 6, x1, roof_bottom - 6),
            radius=10,
            fill=GLASS,
            outline=INK,
            width=3,
        )
        d.line([(x0 + 4, roof_top + 16), (x0 + 4, roof_bottom - 16)], fill=GLASS_HI, width=3)
    d.rounded_rectangle(
        (roof_left, roof_top, roof_right, roof_bottom),
        radius=16,
        fill=shade(body, 1.06),
        outline=INK,
        width=3,
    )
    d.line(
        [(roof_left + 10, roof_top + 8), (roof_right - 10, roof_top + 8)],
        fill=(255, 255, 255, 75),
        width=4,
    )
    if kind == "suv":
        # Roof rails and a sunroof.
        for x in (roof_left + 8, roof_right - 8):
            d.line([(x, roof_top + 12), (x, roof_bottom - 12)], fill=(38, 46, 58, 255), width=5)
        d.rounded_rectangle(
            (roof_left + 22, roof_top + 24, roof_right - 22, roof_bottom - 24),
            radius=8,
            fill=GLASS,
            outline=INK,
            width=3,
        )
    elif kind == "sedan":
        d.rounded_rectangle(
            (roof_left + 24, roof_top + 26, roof_right - 24, roof_bottom - 26),
            radius=8,
            fill=(40, 62, 86, 255),
            outline=INK,
            width=2,
        )

    # Rear window.
    d.polygon(rear_window, fill=GLASS, outline=INK)
    d.line([rear_window[0], rear_window[1]], fill=GLASS_HI, width=4)

    # Trunk / rear deck.
    if kind != "pickup":
        trunk_top = rear_window[2][1] + 4
        d.rounded_rectangle(
            (left + 22, trunk_top, right - 22, bottom - 22),
            radius=12,
            fill=shade(body, 0.94),
            outline=INK,
            width=3,
        )
        d.line(
            [(left + 36, trunk_top + 14), (right - 36, trunk_top + 14)],
            fill=shade(body, 0.75),
            width=3,
        )

    # Mirrors.
    for x0, x1 in ((left - 16, left + 4), (right - 4, right + 16)):
        d.rounded_rectangle(
            (x0, windshield[0][1] + 26, x1, windshield[0][1] + 48),
            radius=7,
            fill=shade(body, 0.92),
            outline=INK,
            width=3,
        )

    # Front bumper, grille, headlights.
    d.rounded_rectangle(
        (left + 18, top + 4, right - 18, top + 24),
        radius=9,
        fill=(33, 42, 52, 255),
        outline=INK,
        width=3,
    )
    d.rounded_rectangle(
        (left + 48, top + 8, right - 48, top + 20),
        radius=4,
        fill=(14, 19, 26, 255),
        outline=(8, 11, 15, 255),
        width=2,
    )
    for x0, x1 in ((left + 24, left + 52), (right - 52, right - 24)):
        d.rounded_rectangle(
            (x0, top + 7, x1, top + 20),
            radius=5,
            fill=(255, 244, 204, 255),
            outline=(120, 110, 80, 255),
            width=2,
        )

    # Rear bumper and tail lights.
    d.rounded_rectangle(
        (left + 18, bottom - 24, right - 18, bottom - 4),
        radius=9,
        fill=(33, 42, 52, 255),
        outline=INK,
        width=3,
    )
    for x0, x1 in ((left + 24, left + 58), (right - 58, right - 24)):
        d.rounded_rectangle(
            (x0, bottom - 20, x1, bottom - 9),
            radius=4,
            fill=(226, 58, 62, 255),
            outline=(90, 12, 16, 255),
            width=2,
        )

    # A final soft roof highlight keeps the top-down view from looking flat.
    d.line(
        [(roof_left + 14, roof_top + 6), (roof_right - 14, roof_top + 6)],
        fill=(255, 255, 255, 90),
        width=4,
    )
    return img


def dot():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse((22, 72, 106, 116), fill=(0, 0, 0, 110))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(8)))
    d.ellipse((24, 24, 104, 104), fill=WHITE, outline=INK, width=4)
    d.ellipse((34, 34, 94, 94), fill=(30, 136, 229, 255), outline=(12, 82, 150, 255), width=3)
    d.ellipse((46, 42, 72, 64), fill=(170, 220, 255, 180))
    return img


def topdown_bike():
    img = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    shadow(img, (150, 120, 362, 420), 110, offset=(8, 16), blur=16, alpha=110)

    # Wheels: front at the top, rear at the bottom.
    for cy in (150, 370):
        d.ellipse((196, cy - 58, 316, cy + 58), fill=(15, 20, 27, 255), outline=INK, width=4)
        d.ellipse((210, cy - 44, 302, cy + 44), fill=(40, 48, 60, 255), outline=(70, 82, 96, 255), width=3)
        d.ellipse((242, cy - 12, 270, cy + 12), fill=HUB, outline=(70, 82, 96, 255), width=2)
        for angle in range(0, 360, 30):
            a = math.radians(angle)
            d.line(
                [(256, cy), (256 + math.cos(a) * 44, cy + math.sin(a) * 44)],
                fill=(150, 162, 176, 180),
                width=2,
            )

    # Frame.
    frame = (242, 163, 60, 255)
    d.line([(256, 370), (256, 280), (256, 210), (256, 150)], fill=frame, width=10)
    d.line([(256, 280), (310, 250)], fill=frame, width=9)
    d.line([(256, 210), (202, 250)], fill=frame, width=9)
    d.line([(202, 250), (256, 280)], fill=frame, width=9)
    d.line([(256, 210), (256, 150)], fill=(255, 255, 255, 90), width=3)
    # Handlebar.
    d.line([(198, 174), (314, 174)], fill=(30, 38, 48, 255), width=12)
    d.ellipse((190, 164, 210, 184), fill=HONEY, outline=INK, width=3)
    d.ellipse((302, 164, 322, 184), fill=HONEY, outline=INK, width=3)
    # Seat.
    d.rounded_rectangle((226, 206, 286, 232), radius=10, fill=(40, 48, 60, 255), outline=INK, width=3)

    # Rider: helmet, shoulders, arms and legs.
    d.ellipse((226, 226, 286, 286), fill=(242, 163, 60, 255), outline=INK, width=4)
    d.ellipse((236, 216, 276, 248), fill=(236, 239, 242, 255), outline=INK, width=3)
    d.rounded_rectangle((214, 278, 298, 326), radius=18, fill=(38, 54, 72, 255), outline=INK, width=3)
    d.line([(226, 288), (208, 182)], fill=(38, 54, 72, 255), width=10)
    d.line([(286, 288), (304, 182)], fill=(38, 54, 72, 255), width=10)
    d.line([(232, 320), (214, 350)], fill=(28, 36, 46, 255), width=12)
    d.line([(280, 320), (298, 350)], fill=(28, 36, 46, 255), width=12)
    d.ellipse((204, 344, 226, 362), fill=(20, 26, 34, 255))
    d.ellipse((290, 344, 312, 362), fill=(20, 26, 34, 255))
    return img


def topdown_walk():
    img = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    shadow(img, (170, 150, 342, 430), 100, offset=(8, 18), blur=16, alpha=105)

    # Legs, one forward and one back.
    d.rounded_rectangle((222, 300, 252, 408), radius=14, fill=(32, 40, 52, 255), outline=INK, width=3)
    d.rounded_rectangle((260, 300, 290, 424), radius=14, fill=(42, 52, 66, 255), outline=INK, width=3)
    d.rounded_rectangle((216, 398, 252, 420), radius=8, fill=(18, 24, 32, 255), outline=INK, width=2)
    d.rounded_rectangle((258, 414, 294, 436), radius=8, fill=(18, 24, 32, 255), outline=INK, width=2)

    # Torso / jacket.
    d.rounded_rectangle((206, 228, 306, 330), radius=26, fill=(226, 82, 54, 255), outline=INK, width=4)
    d.line([(256, 238), (256, 320)], fill=(150, 46, 30, 255), width=3)
    d.rounded_rectangle((214, 300, 298, 338), radius=12, fill=(30, 38, 48, 255), outline=INK, width=2)

    # Arms.
    d.rounded_rectangle((190, 238, 214, 332), radius=12, fill=(200, 68, 44, 255), outline=INK, width=3)
    d.rounded_rectangle((298, 238, 322, 332), radius=12, fill=(200, 68, 44, 255), outline=INK, width=3)
    d.ellipse((188, 320, 212, 344), fill=(214, 158, 120, 255), outline=INK, width=2)
    d.ellipse((300, 320, 324, 344), fill=(214, 158, 120, 255), outline=INK, width=2)

    # Backpack behind the shoulders.
    d.rounded_rectangle((220, 290, 292, 338), radius=14, fill=(24, 32, 42, 255), outline=INK, width=3)
    d.line([(238, 300), (238, 330)], fill=(70, 82, 96, 255), width=3)
    d.line([(274, 300), (274, 330)], fill=(70, 82, 96, 255), width=3)

    # Head, hair and cap.
    d.ellipse((218, 168, 294, 244), fill=(214, 158, 120, 255), outline=INK, width=4)
    d.pieslice((214, 158, 298, 238), 180, 360, fill=(30, 38, 48, 255), outline=INK, width=3)
    d.rounded_rectangle((228, 154, 284, 178), radius=12, fill=(242, 163, 60, 255), outline=INK, width=3)
    d.ellipse((246, 204, 266, 216), fill=(60, 40, 32, 200))
    d.line([(256, 214), (256, 226)], fill=(120, 80, 60, 180), width=2)

    # A little 3D highlight.
    d.line([(222, 244), (222, 314)], fill=(255, 255, 255, 55), width=4)
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
    topdown_car("sedan", (226, 82, 54)).resize((256, 256), Image.Resampling.LANCZOS).save(
        OUT / "ic_puck_sedan_3d.png"
    )
    topdown_car("suv", (244, 191, 56)).resize((256, 256), Image.Resampling.LANCZOS).save(
        OUT / "ic_puck_suv_3d.png"
    )
    topdown_car("pickup", (72, 158, 92)).resize((256, 256), Image.Resampling.LANCZOS).save(
        OUT / "ic_puck_pickup_3d.png"
    )
    dot().save(OUT / "ic_puck_dot.png")
    topdown_bike().resize((256, 256), Image.Resampling.LANCZOS).save(
        OUT / "ic_puck_bike_3d.png"
    )
    topdown_walk().resize((256, 256), Image.Resampling.LANCZOS).save(
        OUT / "ic_puck_walk_3d.png"
    )
    arrow().save(OUT / "ic_puck_arrow_3d.png")
    print("wrote pucks to", OUT)


if __name__ == "__main__":
    main()
