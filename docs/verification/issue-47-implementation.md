# Issue #47 implementation evidence

Issue: <https://github.com/evildarkarchon/sto-aso/issues/47>

Implementation revision: `06322152b2376522f1f852fe04cb82ecbc26f2b1`, on
`improve-codebase-architecture`, based on `758215fdc2dd1cb287afa1db3ecfe7c3131952e1`.
Verification date: 2026-09-04 (America/Los_Angeles).

## Change

Removed the editor's mutable Assignment constructor, field, getter, setter,
model listener role and subscription machinery. AssignmentSelectionPanel now
unbinds through `setAssignmentView(null, null)` after clearing Solutions.
AdmiralPanel continues to own model mutation, subscriptions and invalidation.
Its disposal order and the existing synchronous projection guard are preserved.

The surviving binding documents synchronous complete-state callbacks,
authoritative reprojection, reversible unbinding and preservation of the prior
binding when a non-null view is supplied with a null callback. Existing rendering
adapters, numeric ranges and bound-view requirements remain unchanged. There are
no domain, scoring, persistence, XML, reference-data or visible layout changes.

## Automated verification

Maven 3.9.16 used Eclipse Adoptium Java **25.0.4.1** at `C:\Program Files\jdk`.
Surefire's generated reports confirm `java.version=25.0.4.1`; PATH's standalone
`java` executable is a different version and was not used for the Maven runs.

| Command | Result |
| --- | --- |
| `mvn -Dtest=ArchitectureTest#assignmentEditorCannotRetainOrDirectlyBindMutableAssignment test` before contraction | Expected failure: detected mutable field, constructor, getter, setter and model listener role. |
| `mvn -Dtest=AssignmentPanelTest,ArchitectureTest,AdmiralPanelTest test` | PASS: 45 tests, 0 failures/errors/skips. |
| `mvn clean test` against `0632215` | PASS: 281 tests, 0 failures/errors/skips. Completed 16:24:47 PDT. |
| `git diff --check` | PASS. |
| `graphify update .` after code changes | PASS, AST-only, no LLM/API cost. Final run: 3,193 nodes, 7,774 edges, 352 communities. |

Initial sandbox runs could not clean JUnit temporary directories. The passing
runs above executed outside the sandbox with normal temporary-file access; no
build configuration workaround was committed. A new editor fixture initially
had no Roster cards and therefore no Solution; it was corrected to use the
existing GameData fixture. Expected negative-path tests still print simulated
I/O errors, and Maven retains its existing encoding warnings.

Graphify reported installed skill/package version drift (0.9.44/0.9.53) and
community-label drift; AST regeneration succeeded. Generated graph artifacts
are ignored by Git in this repository and remain refreshed locally.

## Coverage

- `AssignmentPanelTest` covers silent projection, complete synchronous edit
  delivery, normalized authoritative reprojection, absence of optimistic state,
  null-view release of view/callback/Solution with frozen controls, rebinding,
  failed-binding preservation, dependencies and bound-view requirements.
- `ArchitectureTest` now rejects mutable Assignment fields and method/constructor
  signatures, including generic types, and the editor's model-listener role.
  It leaves the root's ownership and real control listeners unrestricted.
- `AdmiralPanelTest` retains supplied GameData Assignment/Event choices, manual
  entry, counts, separate Admiral ownership, navigation, Solution invalidation,
  Ship presentation, exact RosterCard deployment and safe closure. Added tests
  exercise synchronous successive edits in the middle slot, complete value and
  duration preservation, displayed totals and Event critical effects, and stale
  control events emitted synchronously while disposal disables controls.
- Full-suite coverage includes saved-Admiral XML compatibility and the existing
  workspace host closure behavior.

## Comment and Javadoc changes

Removed only legacy-specific Javadocs attached to the deleted mutable constructor,
setter and model `propertyChange` implementation. Rewrote the surviving constructor,
binding/callback, Assignment section disposal, root disposal and manual-workspace
test documentation to describe active ownership and strengthened behavior coverage.
Preserved the projection-suppression, unbinding-before-disabling and intentional
no-op explanations. Added concise Javadocs and a disposal-event reasoning comment
for new test code.

## Reviews and walkthrough

Standards and specification reviews use the fixed diff
`git diff 758215fdc2dd1cb287afa1db3ecfe7c3131952e1...0632215`.

- Standards: no findings.
- Specification: no findings; the review identified only the verification records
  to finalize, documented here and in the walkthrough evidence.
- Visible Swing walkthrough: PASS for Assignment/Event selection, manual numeric
  entry, Solution display/navigation and workspace closure. See
  [walkthrough evidence](issue-47-swing-walkthrough.md) for actions, screenshot,
  matching class fingerprints and limits of the isolated workspace/artwork adapter.
  This was agent-operated visible UI, not human manual verification. Automated
  control interactions above are recorded separately.

Sibling issue [#48](https://github.com/evildarkarchon/sto-aso/issues/48) is open and
its additional event-thread enforcement is not included in this revision. This
ticket is not the last sibling to land. The last landing ticket must run the suite
and walkthrough against the combined revision. This record does not establish
completion of parent [#46](https://github.com/evildarkarchon/sto-aso/issues/46).
