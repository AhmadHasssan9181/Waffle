# design/

The visual language. Everything in the app should come from here rather than hardcoded values.

| Folder | Contents |
| --- | --- |
| `theme/` | Colour tokens, Manrope typography, shapes, `WaffleTheme`. |
| `components/` | Cards, badges, buttons, empty states, spacing scale. |
| `brand/` | `WaffleMark`, `WaffleWordmark`, `WaffleLockup`. |

**The palette is intentionally small:** ink/cream neutrals, honey as the only brand accent, one
desaturated sky accent for "live/GPS", and red/green only when they mean danger/safe. There is no
rainbow theme picker any more — just System / Light / Dark.

**Rules**
- Read colour from `MaterialTheme.colorScheme` or `WaffleColors`, never from a hex literal.
- Read spacing from `WaffleSpace` (4/8/12/16/20/24/32), not ad-hoc `dp`.
- Prefer a hairline border over a drop shadow; shadows turn muddy on the dark ink background.
- New shared UI goes in `components/`, new brand art in `brand/`.