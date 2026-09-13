# Issue #48 implementation evidence

Issue: <https://github.com/evildarkarchon/sto-aso/issues/48>

Implementation revision: `8ff6f7e5da5919e8c53c712b83669e75d3c0bf1f`, on
`improve-codebase-architecture`, based on `c83c7a212d7238b44d9717751fd04e31eca55a99`.
Verification date: 2026-09-04 (America/Los_Angeles).

## Change

Added entry guards to the GameData/artwork constructor and `setShip1`, `setShip2`,
`setShip3`, `clearSolutions`, and `clearShips`. Off-thread calls throw
`IllegalStateException` before initializing editor controls or changing existing
editor state. Constructor dependency validation remains active on the event thread.

Preserved the guards and bound-view preconditions on `setAssignmentView`,
`setAssignmentSolution`, and `clearAssignment`. All declared operations remain
public. Focus-event dispatch, inherited Swing setters, rendering dependencies,
numeric validation, domain behavior, persistence and XML/reference-data formats
are unchanged. No player-visible layout or behavior change was introduced.

## Automated verification

Maven 3.9.16 used Eclipse Adoptium Java **25.0.4.1** at `C:\Program Files\jdk`;
Surefire reports confirm that version. PATH's standalone `java` is Java 26 and
was not used for these builds.

| Check | Outcome |
| --- | --- |
| Constructor regression before guard | Expected failure: off-thread construction returned without an exception, including with overridden presentation cleanup. |
| Three Ship-setter regressions before guards | Expected failures: all three accepted off-thread mutation. |
| Two clear-operation regressions before guards | Expected failures: both accepted off-thread mutation. |
| `mvn -Dtest=AssignmentPanelTest,ArchitectureTest,AdmiralPanelTest,AdmiralWorkspaceHostTest test` | PASS: 61 tests, no failures/errors/skips; 16:44:17 PDT. |
| `mvn clean test` | PASS: 292 tests, no failures/errors/skips; 16:45:24 PDT. |
| `mvn -DskipTests compile` after the final Javadoc wording refinement | PASS; all 259 production classes byte-identical to the clean-build and walkthrough classes. |
| `git diff --check` | PASS. |
| `graphify update .` after code changes | PASS, AST-only, no API cost; 3,212 nodes, 7,827 edges, 342 communities. The final comment-only update found no topology changes. |

An initial new test used `getFirst()` on the `Collection` returned by `ships()`;
the fixture was corrected to use its iterator before recording the expected
behavioral failures. The initial broader sandbox run had no assertion failures,
but JUnit temporary-directory cleanup raised `AccessDeniedException`. The passing
focused and full runs used normal temporary-file access outside the sandbox.
No build workaround was committed. Expected negative-path I/O traces and existing
Maven encoding warnings remain. Graphify reported installed skill/package version
drift (0.9.44/0.9.53) and community-label drift; AST updates succeeded, and generated
graph artifacts remain ignored locally.

## Coverage of the combined revision

`AssignmentPanelTest` now has 18 passing cases. Its mutation cases create and bind
editors on the event thread with a real fixture Solution and three populated Ship
cards, invoke each declared mutation off-thread, then check retained Solution,
all displayed values, bound-view presence, and a later complete edit delivered to
the original owner. Binding replacement and null-view unbinding are both covered.
The constructor test ensures presentation cleanup cannot supply a late guard.
Normal event-thread coverage preserves individual Ship display/clearing,
`clearShips` retaining the Solution, `clearSolutions` releasing it, null Solution
presentation, null dependency failures and bound-view requirements.

Sibling #47's immutable binding contraction is included in this revision. Its
editor binding and architecture tests were rerun. The existing `AdmiralPanelTest`
coverage was audited and retained without redundant tests:

- Supplied GameData Assignment/Event choices and manual entry:
  `assignmentInteractionsUseTheSuppliedGameDataAndPreserveManualEntry`.
- Event critical rating, target critical chance and displayed totals:
  `plannedSolutionDisplaysEventCriticalRatingAndTargetAlongsideShipTotals`.
- Counts, navigation and invalidation:
  `planningContractCoversCountsNavigationInvalidationMessagesAndShortcuts`.
- Successive authoritative edits, complete state and correct middle slot:
  `successiveManualAssignmentEditsSynchronouslyReachTheCorrectSlot`.
- Admiral ownership: `workspaceIsPermanentlyBoundToConstructionTimeAdmiral` and
  `independentWorkspacesCannotCrossContaminateAdmiralState`.
- Exact displayed RosterCard deployment:
  `componentizedFlowSolvesNavigatesAndDeploysTheExactDisplayedRosterCard`.
- Listener ownership, repeated disposal and synchronous stale events:
  `disposalReleasesSoleListenerOwnershipExactlyOnce` and
  `disposalUnbindsBeforeSynchronousDisableEventsAndRejectsStaleEdits`.

The host tests and full suite also retain workspace closure and saved-Admiral
compatibility coverage. Combined visible verification is recorded separately in
[the Swing walkthrough](issue-48-swing-walkthrough.md).

## Comments and review

Expanded the existing constructor, binding, Solution-presentation and
`clearAssignment` Javadocs to state threading/failure semantics. Added Javadoc to
the five newly guarded methods and new tests/helpers. No comments were deleted;
the existing projection, lifetime and focus-dispatch explanations were preserved.

Independent reviews examined the implementation against baseline `c83c7a2` and
issue #48. The committed equivalent is `git diff c83c7a2...8ff6f7e`.

- Standards: no violations or actionable smells. Applied the optional wording
  refinement to say constructor rejection precedes editor **controls**, since
  superclass construction and field initializers precede the constructor body.
- Specification: no implementation findings. Required full-suite, graph update
  and walkthrough evidence was subsequently completed and recorded here.

This is combined local verification for #47 and #48. The changes are committed
locally; this record does not assert that the branch has been published or that
the GitHub parent issue #46 has been closed.
