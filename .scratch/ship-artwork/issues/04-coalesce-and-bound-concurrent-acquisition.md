Status: resolved
Blocked by: 03

# 04: Coalesce and bound concurrent acquisition

## What to build

Coordinate startup and on-demand acquisition inside Ship Artwork so repeated demand shares work and background artwork activity remains bounded while still making progress after every success or failure.

## Acceptance criteria

- [x] Concurrent startup and on-demand requests for one internal remote-image identity share exactly one active acquisition.
- [x] At most three remote acquisitions are active at once.
- [x] Queued work continues when an active attempt succeeds, fails, is cancelled, or is interrupted.
- [x] Generic presentation never joins or creates acquisition work.
- [x] Coordination remains internal; callers cannot provide executors, inspect queues, or obtain acquisition futures.
- [x] Deterministic tests exercise overlap and completion ordering without wall-clock sleeps.

## Comments

Implemented internal source-name coalescing and a FIFO queue limited to three
active acquisitions. Each waiting canonical Ship composes its own artwork from
the shared source, and later aliases reuse successfully acquired pixels. Generic
presentation does not participate. Duplicate callbacks cannot release capacity
twice; synchronous completion cannot recursively drain the queue. Terminal null
results and synchronous start failures release capacity without clearing thread
interruption. Closing abandons queued demand and ignores late callbacks.

Deterministic public-seam tests cover startup overlap, racing lookups, shared
source publication, generic isolation, out-of-order completion, failures,
cancellation, interrupted completion, duplicate callbacks and queued shutdown.
The internal adapter contract now explicitly requires terminal notification for
failure, cancellation and interruption. Production remains offline; real transport,
retry/backoff and bounded shutdown completion belong to later tickets.

Verification: focused artwork tests and `.\gradlew.bat clean build` passed,
including the full suite and exploded, packaged and thin-JAR checks. Spec review
reported no findings. Standards review requested explicit monitor-ownership
documentation, which was added. Expanded the existing internal constructor
Javadoc to document terminal outcomes; no explanatory comments were removed.
