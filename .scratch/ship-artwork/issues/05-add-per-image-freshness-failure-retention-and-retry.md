Status: ready-for-agent
Blocked by: 04

# 05: Add per-image freshness, failure retention, and retry

## What to build

Make each remote image independently refreshable and recoverable so successful pixels remain visible through transient failures while failed work retries at a controlled pace rather than being suppressed for an entire archive interval.

## Acceptance criteria

- [ ] Seven-day freshness is recorded and evaluated per successfully acquired remote image.
- [ ] Scheduling, failure, cancellation, and interruption do not advance freshness.
- [ ] Repeated failures for one image apply one-, five-, and thirty-minute in-process backoff steps without blocking unrelated images.
- [ ] Success resets the failure sequence, while explicit online refresh and the next process launch bypass prior in-process backoff.
- [ ] A failed attempt retains stale last-known-good artwork when available and otherwise retains generic artwork.
- [ ] One coalesced failure produces one non-modal diagnostic.
- [ ] A successful refresh with unchanged pixels updates freshness without causing a false repaint.
