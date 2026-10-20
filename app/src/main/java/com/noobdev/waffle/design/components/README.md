# design/components

Reusable, theme-aware building blocks. Keep signatures stable — several screens depend on them.

- **Status:** `StatusBadge`, `DisasterTypeBadge`, `statusColor()`, `disasterColor()`, `disasterIcon()`.
- **Surfaces:** `WaffleCard`, `SoftDivider`, `ActionTile`, `MetricCard`.
- **Alerts:** `DisasterAlertCard` (used by Home and Alerts).
- **Buttons:** `WafflePrimaryButton`, `WaffleSecondaryButton`, `EmergencyButton`, `QuickActionButton`.
- **States:** `WaffleLoadingSpinner`, `LoadingOverlay`, `EmptyState`, `AllClearCard`.
- **Text:** `SectionHeader`, `Eyebrow`, `InfoRow`.
- **Spacing:** `WaffleSpace` — the only place `4.dp` etc. should be defined.

Add a component here only when at least two screens need it. One-off layout stays in its feature.