# design/brand

| Composable | Use |
| --- | --- |
| `WaffleMark` | The waffle-quadcopter mark alone. Headers, list rows. |
| `WaffleWordmark` | Mark + "Waffle" + optional subtitle. Home header. |
| `WaffleLockup` | Large mark + wordmark. Permission screen and empty states. |

The PNG (`res/drawable-xxxhdpi/ic_waffle_mark.png`, 288 px) is rasterised from `branding/mark.svg`
with headless Chrome. Android's `VectorDrawable` cannot render the source SVG (filters, clip paths,
`<use>`), so do not try to convert it to a vector drawable.

To regenerate the launcher icons and mark, see `branding/README.md`.