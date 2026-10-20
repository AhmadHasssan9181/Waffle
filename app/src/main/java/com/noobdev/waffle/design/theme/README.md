# design/theme

| File | Responsibility |
| --- | --- |
| `Color.kt` | Brand tokens (`Honey`, `Ink`, `Cream`, `Sky`), semantic status colours, and backwards-compatible aliases for older screens. |
| `Type.kt` | Manrope font family, the Material 3 type scale, and `WaffleTextStyles` (eyebrow, metric, timestamp, coordinates). |
| `Theme.kt` | `WaffleTheme`, the `AppTheme` enum (`SYSTEM`/`LIGHT`/`DARK`), light/dark schemes, shapes, and `WaffleColors`. |

**Manrope** is bundled as five static TTFs in `res/font/` generated from the variable font, so
weights render correctly on API 24+. Licence: SIL OFL, see `branding/OFL-Manrope.txt`.

**Adding a colour:** add it to `Color.kt` with a semantic name, then expose it through
`WaffleColors` if screens should use it by meaning. Do not add a sixth hue.