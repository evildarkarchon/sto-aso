Status: resolved
Blocked by: 06, 08, 09, 10

# 13: Transfer application ownership to Ship Artwork

## What to build

Make application startup and shutdown own exactly one Ship Artwork instance, with GameData and Roster state established before background artwork work begins and without exposing Icon Cache operations through process-global state.

## Acceptance criteria

- [x] Startup resolves the data directory through the existing application rule, loads GameData and Admirals, and then opens Ship Artwork before constructing a Swing frame.
- [x] Initial current-Roster Ship types are supplied directly for module-owned prewarming.
- [x] Application state exposes the owned Ship Artwork instance at composition roots but no caller-visible Icon Cache.
- [x] Icon freshness, download eligibility, and scheduling are removed from bootstrap and the general Swing worker executor while GameData Refresh remains intact.
- [x] Console shutdown closes Ship Artwork instead of saving an Icon Cache directly.
- [x] Optional artwork load, acquisition, recovery, or persistence failure remains nonfatal to application startup and exit.
- [x] Bootstrap and packaged-entry verification continue to honor the accepted data-directory decision.

## Comments

Resolved 2026-09-26. Bootstrap now resolves the existing GameData directory, loads
GameData and Admirals, opens one Ship Artwork lifetime with the canonical current-
Roster union, and publishes it before optional GameData Refresh scheduling. App
exposes Ship Artwork rather than Icon Cache. A temporary factory adapter lets the
remaining Swing presentation roots use the owned module until tickets 14–16 move
their renderer interfaces to canonical Ships. Console shutdown closes the module;
the orphan icon loader and icon work in the general Swing executor were removed.

The bootstrap test checks ordered opening, the exact canonical Roster union,
single ownership, and nonfatal corrupt legacy artwork without deleting the
rollback archive. Focused bootstrap, App state, standalone-entry, workspace,
and artwork-baseline tests passed. `.\gradlew.bat clean build` passed the full
JUnit suite, exploded and packaged bootstrap probes, and thin-JAR verification.
The accepted executable/CWD/CWD-`data` directory rule and GameData Refresh path
remain intact. No GameData or Admirals XML format change was made.
Independent standards and spec reviews found no remaining issues after the
temporary adapter's parameter Javadoc was completed.
