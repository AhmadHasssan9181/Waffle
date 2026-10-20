# core/network

`NetworkState` answers two questions:
- `isOnline(context)` — should we attempt a network call at all?
- `isUnmetered(context)` — may we download a large offline map pack without asking?

Both are used to avoid burning a user's data or hanging on a dead connection during a disaster.