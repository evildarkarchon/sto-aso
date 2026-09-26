Status: resolved
Blocked by: 04, 05, 07

# 08: Make persistence timely and shutdown bounded

## What to build

Retain successfully acquired artwork promptly without rewriting the archive for every image, and ensure closing Ship Artwork cannot hang application exit or lose work that completed within the bounded shutdown window.

## Acceptance criteria

- [x] On-demand successes are persisted two seconds after the most recent success.
- [x] Continuous successful acquisition cannot postpone persistence beyond thirty seconds.
- [x] Startup prewarming is persisted once for the completed batch rather than once per Ship.
- [x] Close stops accepting new acquisition, allows a short bounded grace period, cancels remaining work, and flushes completed artwork.
- [x] Cancelled or interrupted work does not advance freshness and is due again on the next launch.
- [x] Close preserves thread interruption, is idempotent, and cannot wait indefinitely for network work.
- [x] Timing, ordering, and restart-visible outcomes are tested deterministically without wall-clock sleeps.

## Comments

Implemented two-second debounce with a thirty-second maximum using an internal monotonic scheduler. Startup successes remain visible immediately but enter persistence as a completed batch; mixed on-demand saves retain the prior startup pixels and freshness together. Archive installation runs outside the lookup monitor and serializes snapshots without losing successes arriving during a write.

Close rejects demand, gives active requests a two-second grace, cancels remaining transport work, and flushes every completed image. Interrupted completion cannot advance freshness; unfinished refreshes remain due after restart. Concurrent and repeated closes share one shutdown, and interruption is preserved.

Deterministic regression tests cover debounce, maximum delay, synchronous and delayed startup batches, mixed demand, prior durable freshness, interrupted close/completion, and saves racing acquisition or shutdown. Separate standards and spec reviews completed with no remaining findings after fixing mixed-batch and final-flush ordering gaps. The previous close Javadoc and immediate-abort comment were rewritten because shutdown now grants a grace period. No public interface, archive schema, XML compatibility, or visible pixel recipe changed.

Validation: focused `ShipArtworkTimingTest` and artwork-package tests passed. Final `.\gradlew.bat clean build` passed all tests plus exploded/packaged bootstrap and thin-JAR verification.
