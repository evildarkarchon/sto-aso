# Issue #47 visible Swing walkthrough

Recorded on 2026-09-04 at approximately 16:29 America/Los_Angeles.

This was an **agent-operated walkthrough of an actual visible Swing window**, using the Windows computer-use skill and `@oai/sky` mouse/keyboard input. It was not human manual verification, a headless test, or a programmatic `doClick` simulation. Screenshots were inspected after individual actions.

## Revision and environment

- Verified implementation revision: `06322152b2376522f1f852fe04cb82ecbc26f2b1`.
- The harness initially copied compiled production classes from the worktree based on `758215fdc2dd1cb287afa1db3ecfe7c3131952e1` with the issue #47 changes applied. After the implementation commit and its clean build, all **259 of 259 production `.class` files** in the copied launch tree were compared by SHA-256 with `target/classes`; every file was byte-identical. The visible process therefore exercised the implementation revision above.
- Windows desktop; JDK 25, launched using `C:\Program Files\jdk\bin\javaw.exe` (resolved process executable under `OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1`).
- Production `AdmiralPanel` and its production children, listeners, planning logic, and renderers were displayed in a temporary `JFrame` titled `Issue 47 - isolated production Admiral workspace`.
- Reference data came from this repository's `data/`. The harness created an in-memory Admiral named `Issue 47 isolated walkthrough` and added the first six reference Ships as Active Ships. It neither loaded nor saved the user's Admirals.
- The supplied data directory was `.scratch/issue-47-walkthrough/visible-data`. The `ShipIconFactory` adapter returned the existing `Images.ICON_BLANK`; text, statistics, layout, and bundled stat symbols used production rendering, but Ship artwork itself was deliberately blank.

The local harness is `.scratch/issue-47-walkthrough/Issue47Walkthrough.java`, SHA-256 `B818C26B6D72E3EB66C4FA0BFB614E390424E4392F45C5433DDC1EECDCB1F6C6`. Its frame close listener called `AdmiralPanel.dispose()` on the event dispatch thread before disposing the frame. Copied dependencies and classes kept the running process independent of the concurrent clean Maven build.

Source fingerprints at verification:

| Source | SHA-256 |
| --- | --- |
| `src/com/kor/admiralty/ui/AssignmentPanel.java` | `8180404249A844B238CDCC7883F9FA4BE14239742345E4372EA0E75D1F07B28A` |
| `src/com/kor/admiralty/ui/panels/AssignmentSelectionPanel.java` | `E0C523BB44B6FF1425D0EE41C8425586F533F2DA50E1FDCE22C3F89ECF52E076` |

## Actions and observed outcomes

| Flow | Actual UI actions | Observed result |
| --- | --- | --- |
| Assignment selection | Opened Assignments, opened the Assignment dropdown, clicked `Analyze Newly Discovered Phenomenon`. | Selection remained visible; required ENG/TAC/SCI became 45/45/105. |
| Event selection | Opened the Event dropdown, clicked `Abandoned Treasure Trove`, opened Assignment Stats. | Event name remained visible; Event ENG/TAC/SCI were 0/0/0 and Crit Rating was 10. |
| Manual entry | Focused Required ENG, selected its existing value, entered 60 with native numpad keys, and pressed Tab. Entered Event ENG 5 and pressed Tab. | Fields retained Required ENG 60 and Event ENG 5. Planning subsequently used combined ENG requirement 65. The initial `type_text` attempt produced no visible digits in Swing; native numpad key input succeeded. |
| Solution display | Clicked Plan Assignments, then Assigned Ships. | A calculated Solution appeared: Advanced Escort (T6), Advanced Heavy Cruiser Retrofit, and Advanced Escort. Totals displayed ENG 79/65, TAC 154/45, SCI 70/105, CRIT 133/0. Ship labels, statistics, and trait text rendered without an exception dialog. |
| Solution navigation | Clicked Next. | Middle Ship changed to Advanced Light Cruiser (T6); totals became ENG 87/65, TAC 150/45, SCI 64/105, CRIT 170/0. Prev and Best became enabled. The manual combined ENG requirement remained 65. |
| Workspace closure | Clicked the temporary frame's title-bar Close button. | Root disposal returned on the event dispatch thread, the frame closed, a refreshed Windows window listing contained no matching window, and the process exited. |

The isolated process's final standard output was:

```text
WORKSPACE_VISIBLE
WORKSPACE_DISPOSE_RETURNED_ON_EDT=true
FRAME_CLOSED
```

Its standard error log was empty. [Observed Solution screenshot](issue-47/solution.png) records the actual rendered result before Next was clicked.

## Scope and remaining boundaries

The four requested flows—Assignment/Event selection, manual entry, Solution display, and workspace closure—were exercised in the production workspace. Closure covered the real root's disposal through the harness frame; this was not a walkthrough of the full `AdmiraltyConsole` host, application startup/download behavior, persisted Admiral switching, or application shutdown persistence. Listener detachment, reentrant callbacks, and stale-intent invariants require the separate automated regression tests; window disappearance alone does not prove those internal properties.

No claim is made about Ship artwork fidelity, exhaustive visual parity, or a before/after screenshot comparison. The screenshot shows blank Ship artwork by design. This record supplies focused issue #47 evidence only; parent issue #46 and the separate event-thread follow-up #48 are not declared complete by this walkthrough.
