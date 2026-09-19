Status: resolved
Blocked by: 04

# 05: Add per-image freshness, failure retention, and retry

## What to build

Make each remote image independently refreshable and recoverable so successful pixels remain visible through transient failures while failed work retries at a controlled pace rather than being suppressed for an entire archive interval.

## Acceptance criteria

- [x] Seven-day freshness is recorded and evaluated per successfully acquired remote image.
- [x] Scheduling, failure, cancellation, and interruption do not advance freshness.
- [x] Repeated failures for one image apply one-, five-, and thirty-minute in-process backoff steps without blocking unrelated images.
- [x] Success resets the failure sequence, while explicit online refresh and the next process launch bypass prior in-process backoff.
- [x] A failed attempt retains stale last-known-good artwork when available and otherwise retains generic artwork.
- [x] One coalesced failure produces one non-modal diagnostic.
- [x] A successful refresh with unchanged pixels updates freshness without causing a false repaint.

## Comments

Resolved 2026-09-18. Ship Artwork records successful completion time per remote source,
checks freshness on subsequent specific lookups, and retains source pixels through failed
refreshes. Retry eligibility follows one-, five-, then capped thirty-minute delays;
success resets the sequence. The internal operator refresh entry point bypasses freshness
and backoff while sharing pending acquisitions. Retry state remains lifetime-local.
All existing presentations of a shared source update on success, and late lookups show
retained pixels immediately. Failed coalesced attempts log once; equal refreshed pixels
do not repaint visible owners.

Seven deterministic seam tests cover freshness boundaries, delayed completion, independent
backoff, success reset, explicit refresh, reopening, cancellation/interruption, obsolete
callbacks, diagnostics, shared-source retention and unchanged-pixel repaint suppression.
Focused artwork tests and `.\gradlew.bat clean build` passed, including the full test suite
and exploded/packaged bootstrap verification. Standards and spec reviews reported no
material findings. Production GitHub transport, persisted freshness and operator CLI wiring
remain with their separate implementation tickets.
