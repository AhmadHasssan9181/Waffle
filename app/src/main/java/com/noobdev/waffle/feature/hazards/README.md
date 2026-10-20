# feature/hazards

Early sketch of standalone hazard cards (earthquake / flood / wildfire) with TODO logic.

**Status: not wired into navigation.** It is kept because the card copy is useful, but the live
hazard UI is `feature/map` + `feature/alerts`. If you build on it, move the shared pieces into
`design/components` instead of importing this package from a screen.

Note: this file defines its own `InfoRow`, separate from `design/components.InfoRow`. Do not
wildcard-import both packages in the same file.