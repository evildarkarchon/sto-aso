Status: ready-for-agent
Blocked by: 06, 08, 09, 10

# 13: Transfer application ownership to Ship Artwork

## What to build

Make application startup and shutdown own exactly one Ship Artwork instance, with GameData and Roster state established before background artwork work begins and without exposing Icon Cache operations through process-global state.

## Acceptance criteria

- [ ] Startup resolves the data directory through the existing application rule, loads GameData and Admirals, and then opens Ship Artwork before constructing a Swing frame.
- [ ] Initial current-Roster Ship types are supplied directly for module-owned prewarming.
- [ ] Application state exposes the owned Ship Artwork instance at composition roots but no caller-visible Icon Cache.
- [ ] Icon freshness, download eligibility, and scheduling are removed from bootstrap and the general Swing worker executor while GameData Refresh remains intact.
- [ ] Console shutdown closes Ship Artwork instead of saving an Icon Cache directly.
- [ ] Optional artwork load, acquisition, recovery, or persistence failure remains nonfatal to application startup and exit.
- [ ] Bootstrap and packaged-entry verification continue to honor the accepted data-directory decision.
