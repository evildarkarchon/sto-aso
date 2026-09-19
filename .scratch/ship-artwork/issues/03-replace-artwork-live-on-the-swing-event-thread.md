Status: ready-for-agent
Blocked by: 02

# 03: Replace artwork live on Swing's event thread

## What to build

Allow asynchronously acquired pixels to advance an already-returned Ship Artwork handle and repaint visible Swing owners without requiring callers to reopen a view, replace the handle, or subscribe to an update protocol.

## Acceptance criteria

- [ ] A deterministic scripted acquisition can replace fallback pixels in the existing handle after lookup has returned.
- [ ] Pixel delegate replacement and repaint requests occur on Swing's event-dispatch thread.
- [ ] Paint owners are tracked weakly so unreachable or disposed views are not retained or repainted.
- [ ] Repaint requests for one update are coalesced and target the useful owners of that handle.
- [ ] Live replacement does not publish list-model events, rebuild a Ship Filter, change filter criteria, or change selected identities.
- [ ] Callers receive no listener, future, cache key, or acquisition protocol through the public interface.
