Status: ready-for-agent
Blocked by: 04, 05, 07

# 08: Make persistence timely and shutdown bounded

## What to build

Retain successfully acquired artwork promptly without rewriting the archive for every image, and ensure closing Ship Artwork cannot hang application exit or lose work that completed within the bounded shutdown window.

## Acceptance criteria

- [ ] On-demand successes are persisted two seconds after the most recent success.
- [ ] Continuous successful acquisition cannot postpone persistence beyond thirty seconds.
- [ ] Startup prewarming is persisted once for the completed batch rather than once per Ship.
- [ ] Close stops accepting new acquisition, allows a short bounded grace period, cancels remaining work, and flushes completed artwork.
- [ ] Cancelled or interrupted work does not advance freshness and is due again on the next launch.
- [ ] Close preserves thread interruption, is idempotent, and cannot wait indefinitely for network work.
- [ ] Timing, ordering, and restart-visible outcomes are tested deterministically without wall-clock sleeps.
