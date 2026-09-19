---
status: accepted
---

# Use stable live ImageIcon handles for Ship Artwork

The Ship Artwork module returns one immediate, stable, read-only `ImageIcon` for
a canonical Ship and generic-or-specific presentation. The handle privately
advances from generic or stale pixels to fresh artwork and arranges targeted
Swing repaint on the event thread. This deliberately Swing-specific seam was
chosen over explicit listeners, portfolio snapshots and public batch operations
because it gives every renderer the smallest interface while hiding acquisition,
fallback, cache identity, concurrency and repaint coordination behind one deep
module.

## Consequences

- Live handles contain mutable implementation state and require focused tests for
  weak paint-owner tracking, event-thread replacement and disposed-view lifetime.
- The application interface remains small; bootstrap prewarming and operator
  migration use the same implementation without exposing their protocols to UI
  callers.
- Moving presentation away from Swing would require a new Ship Artwork seam. That
  cost is accepted in exchange for greater leverage and locality in the current
  Java Swing application.

