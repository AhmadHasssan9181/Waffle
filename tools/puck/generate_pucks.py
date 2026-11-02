"""Generates the 3D navigation pucks (car, bike, walk) as 256px PNGs.

The puck is an extruded honey arrow with a small white mode badge. The map rotates the bitmap with
the bearing, so the arrow points up in the image.
Run with: python tools/puck/generate_pucks.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res" / "drawable-nodpi"
SIZE = 256

INK = (27, 36, 48, 255)
HONEY = (242, 163, 60, 255)
HONEY_LIGHT = (255, 202, 112, 255)
HONEY_DARK = (178, 96, 16, 255)
WHITE = (255, 255, 255, 255)


def canvas():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def shadow(base, box, alpha=80, blur=36):
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse(box, fill=(0, 0, 0, alpha))
    base.alpha_composite(layer.filter(ImageFilter.GaussianBlur(blur)))


def gradient(size, top, bottom):
    img = Image.new("RGBA", (1, size[1]), (0, 0, 0, 0))
    for y in range(size[1]):
        t = y / max(1, size[1] - 1)
        color = tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(4))
        ImageDraw.Draw(img).point((0, y), fill=color)
    return img.resize(size, Image.Resampling.BILINEAR)


def masked_polygon(base, points, top, bottom):
    w, h = base.size
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).polygon(points, fill=255)
    layer = gradient((w, h), top, bottom)
    base.paste(layer, (0, 0), mask)


def puck(mode):
    img = canvas()
    shadow(img, (40, 188, 216, 238), alpha=95, blur=12)
    d = ImageDraw.Draw(img)

    # Extruded side of the arrow (offset down/right).
    depth = [(128, 48), (232, 222), (128, 182), (24, 222)]
    d.polygon(depth, fill=HONEY_DARK)
    d.line(depth + [depth[0]], fill=INK, width=8, joint="curve")

    # Top face of the arrow.
    top = [(128, 18), (214, 190), (128, 150), (42, 190)]
    masked_polygon(img, top, HONEY_LIGHT, HONEY)
    d.polygon(top, outline=WHITE, width=7)
    d.line([(128, 30), (128, 146)], fill=(255, 255, 255, 55), width=7)

    # Mode badge.
    d.ellipse((70, 116, 186, 232), fill=(0, 0, 0, 70))
    d.ellipse((72, 110, 184, 222), fill=WHITE, outline=INK, width=7)

    if mode == "car":
        d.rounded_rectangle((96, 138, 160, 188), radius=12, fill=INK)
        d.rounded_rectangle((106, 146, 150, 166), radius=8, fill=(58, 74, 92, 255))
        d.rounded_rectangle((88, 144, 100, 160), radius=5, fill=(14, 19, 26, 255))
        d.rounded_rectangle((156, 144, 168, 160), radius=5, fill=(14, 19, 26, 255))
        d.rounded_rectangle((104, 136, 118, 144), radius=4, fill=HONEY_LIGHT)
        d.rounded_rectangle((138, 136, 152, 144), radius=4, fill=HONEY_LIGHT)
        d.rounded_rectangle((104, 182, 118, 190), radius=4, fill=(220, 65, 72, 255))
        d.rounded_rectangle((138, 182, 152, 190), radius=4, fill=(220, 65, 72, 255))
    elif mode == "bike":
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


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for mode in ("car", "bike", "walk"):
        puck(mode).save(OUT / f"ic_puck_{mode}_3d.png")
    print("wrote 3 pucks to", OUT)


if __name__ == "__main__":
    main()
