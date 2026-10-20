# branding

Source art for the app icon and in-app mark.

| File | Purpose |
| --- | --- |
| `mark.svg` | The waffle-quadcopter mark on a transparent 512×512 canvas. Source for the in-app PNG. |
| `icon.svg` | Launcher artwork: cream rounded square + mark. |
| `icon_foreground.svg` | Adaptive-icon foreground (scaled to survive the circular mask). |
| `icon-512.png`, `mark-288.png` | Rendered previews. |
| `Manrope-Variable.ttf`, `_fonts.py` | Font source + the script that instances static weights. |
| `OFL-Manrope.txt` | Manrope licence (SIL Open Font License). |

## Regenerating the assets

Android's `VectorDrawable` cannot render these SVGs (they use `filter`, `clipPath`, `<use>`, and
per-element gradients), so they are rasterised with headless Chrome — no ImageMagick/Inkscape
needed.

1. Render `icon.svg` at 48/72/96/144/192 px into `app/src/main/res/mipmap-*/ic_launcher.png`
   (copy to `ic_launcher_round.png`).
2. Render `mark.svg` at 288 px into `app/src/main/res/drawable-xxxhdpi/ic_waffle_mark.png`.
3. Render `icon_foreground.svg` at 432 px into `app/src/main/res/drawable/ic_launcher_foreground.png`.
4. `drawable/ic_launcher_background.xml` is a cream radial gradient matching `icon.svg`.

Chrome command used:

```
chrome --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
  --default-background-color=00000000 --window-size=512,512 \
  --screenshot=out.png render.html
```

`render.html` is a transparent page containing a full-viewport `<img src="...svg">`.

Fonts: `python branding/_fonts.py` downloads the Manrope variable font and instances the five
weights into `app/src/main/res/font/`.