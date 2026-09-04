# Issue #48 combined visible Swing walkthrough

Recorded on 2026-09-04, approximately 16:45–16:48 America/Los_Angeles.

This was an **agent-operated walkthrough of an actual visible Swing window**,
using the Windows computer-use skill and `@oai/sky` mouse/keyboard input with
screenshots inspected after individual actions. It was not human manual testing.
Headless tests and programmatic control interactions are recorded separately in
[the implementation evidence](issue-48-implementation.md).

## Revision and environment

- Verified implementation: `8ff6f7e5da5919e8c53c712b83669e75d3c0bf1f`, including
  sibling #47's binding contraction from `0632215`.
- Windows; Java **25.0.4.1**, launched with `C:\Program Files\jdk\bin\javaw.exe`.
- Production `AdmiralPanel`, child controls, listeners, planning logic and
  renderers in a temporary frame titled
  `Issue 48 - isolated production Admiral workspace`.
- The harness created an in-memory Admiral named `Issue 48 isolated walkthrough`
  with the first six Ships from repository `data/` marked Active. It did not load
  or save the user's Admirals. The supplied data path was
  `.scratch/issue-48-walkthrough/visible-data`.
- The existing `ShipIconFactory` adapter returned `Images.ICON_BLANK`. Ship names,
  statistics, Special Ability text, layout and bundled stat symbols used production
  rendering; Ship artwork fidelity was not tested.

The local harness `.scratch/issue-48-walkthrough/Issue48Walkthrough.java` has
SHA-256 `AE6A4B9D19F9B0254E113FA8F07919792B406A365A68D529B2EF21366FF2E00B`.
It reused #47's isolated harness with only the class and window/Admiral labels
changed. Its window-close listener called `AdmiralPanel.dispose()` on the event
thread before disposing the frame.

The process launched from copied compiled classes to remain independent of the
clean Maven build. All **259 of 259 production class files** matched `target/classes`
by SHA-256 after that build, and again after compiling committed revision `8ff6f7e`
with the final comment-only wording refinement. There were zero mismatches.
The committed `AssignmentPanel.java` source has SHA-256
`1043BBD80F0C942C5D52862F3A943C9AB9F7C4FEB327748E781D7C5B9ED3759A`.

## Actions and observations

| Flow | Actual UI actions | Observed outcome |
| --- | --- | --- |
| Assignment selection | Opened Assignments, expanded the Assignment dropdown, clicked `Analyze Newly Discovered Phenomenon`. | Selection remained visible; requirements were ENG/TAC/SCI 45/45/105. |
| Event selection | Expanded the Event dropdown, clicked `Abandoned Treasure Trove`, opened Assignment Stats. | Event fields showed ENG/TAC/SCI 0/0/0 and Crit Rating 10. |
| Manual entry | Focused Required ENG and entered 60 with native numpad keys. Clicked Event ENG to commit, entered 5, then pressed Tab. | Required ENG remained 60 and Event ENG remained 5. Subsequent planning used combined ENG requirement 65. |
| Solution display | Clicked Plan Assignments, then Assigned Ships. | Advanced Escort (T6), Advanced Heavy Cruiser Retrofit and Advanced Escort appeared. Displayed totals were ENG 79/65, TAC 154/45, SCI 70/105, CRIT 133/0. Names, stats and Special Ability text rendered with no exception dialog. |
| Solution navigation | Clicked Next. | Middle Ship changed to Advanced Light Cruiser (T6); totals became ENG 87/65, TAC 150/45, SCI 64/105, CRIT 170/0. Prev and Best enabled; manual combined ENG remained 65. |
| Workspace closure | Clicked the frame's title-bar Close button. | Root disposal returned on the event thread and the frame closed. A follow-up window listing had no matching window; process 13832 had exited. |

[Observed Solution screenshot](issue-48/solution.png) records the actual display
before navigating Next. The final stdout was:

```text
WORKSPACE_VISIBLE
WORKSPACE_DISPOSE_RETURNED_ON_EDT=true
FRAME_CLOSED
```

Stderr was empty. The immediate window listing after Close briefly still listed
the closing frame; a subsequent listing was empty and the process had exited.

## Boundaries and gaps

All requested focused walkthrough flows were exercised against the combined
implementation; none remains outstanding. The walkthrough covers closure of the
production workspace through the harness frame. Full `AdmiraltyConsole` startup,
downloads, persisted Admiral switching and shutdown persistence were outside this
walkthrough. Internal listener detachment, exact deployment identity, reentrant
disposal and stale-intent invariants are established by the separately recorded
automated tests, not by window disappearance.

No exhaustive visual parity or Ship artwork claim is made. No separately agreed
visible change occurred, so the screenshot is an observation record rather than a
before/after comparison. This record supplies combined local #46/#47/#48
verification; it does not publish the branch or close GitHub issues.
