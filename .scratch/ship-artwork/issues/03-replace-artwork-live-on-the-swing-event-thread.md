Status: resolved
Blocked by: 02

# 03: Replace artwork live on Swing's event thread

## What to build

Allow asynchronously acquired pixels to advance an already-returned Ship Artwork handle and repaint visible Swing owners without requiring callers to reopen a view, replace the handle, or subscribe to an update protocol.

## Acceptance criteria

- [x] A deterministic scripted acquisition can replace fallback pixels in the existing handle after lookup has returned.
- [x] Pixel delegate replacement and repaint requests occur on Swing's event-dispatch thread.
- [x] Paint owners are tracked weakly so unreachable or disposed views are not retained or repainted.
- [x] Repaint requests for one update are coalesced and target the useful owners of that handle.
- [x] Live replacement does not publish list-model events, rebuild a Ship Filter, change filter criteria, or change selected identities.
- [x] Callers receive no listener, future, cache key, or acquisition protocol through the public interface.

## Comments

Implemented internal scripted acquisition completion, private composition, and EDT
publication into the existing read-only handle. Generic handles remain unchanged.
Paint owners are weak references; renderer descendants resolve to their
CellRendererPane host, with one repaint per visible owner per update. Hidden,
disposed and collected owners are pruned. Closing rejects queued completions.
The public creation, lookup and close interface is unchanged; production opening
remains offline, with real transport and acquisition coordination in later tickets.

Regression tests cover deferred EDT publication, stable identity, generic pixels,
repaint thread and coalescing, hidden owners, renderer hosts, weak reclamation,
and completion after close. An unrelated weak control distinguishes JVMs ignoring
explicit GC from retained artwork owners. Actual Ship Filter presentation tests
verify unchanged controls, criteria, list/model identities and selected Ship,
with no list-model events. No layout or composition recipe changed.

Standards and spec reviews completed without blocking findings. Updated existing
class/method Javadocs to describe live ownership, painting and close behavior;
no accurate explanatory comments were removed.

Verification: focused artwork and Ship Filter integration tests passed;
`.\gradlew.bat clean build` passed using the configured Java 25 toolchain,
including the full test suite and exploded, packaged and thin-JAR checks.
