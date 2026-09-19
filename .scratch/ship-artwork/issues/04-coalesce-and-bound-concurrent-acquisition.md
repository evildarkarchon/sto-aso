Status: ready-for-agent
Blocked by: 03

# 04: Coalesce and bound concurrent acquisition

## What to build

Coordinate startup and on-demand acquisition inside Ship Artwork so repeated demand shares work and background artwork activity remains bounded while still making progress after every success or failure.

## Acceptance criteria

- [ ] Concurrent startup and on-demand requests for one internal remote-image identity share exactly one active acquisition.
- [ ] At most three remote acquisitions are active at once.
- [ ] Queued work continues when an active attempt succeeds, fails, is cancelled, or is interrupted.
- [ ] Generic presentation never joins or creates acquisition work.
- [ ] Coordination remains internal; callers cannot provide executors, inspect queues, or obtain acquisition futures.
- [ ] Deterministic tests exercise overlap and completion ordering without wall-clock sleeps.
