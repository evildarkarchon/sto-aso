# Assignment editor seam — design decisions

Confirmed in the architecture review and grilling session on 2026-09-04.
This document specifies a subsequent implementation change; it does not claim
that the migration or its verification has been completed.

Domain terms follow `CONTEXT.md`. Architecture terms follow `/codebase-design`:
module, interface, implementation, depth, seam, adapter, leverage, and locality.

## Problem and evidence

The active Assignment editor already receives an immutable `AssignmentView`
and reports intended edits through its workspace root. `AssignmentPanel` also
retains a legacy mutable-`Assignment` constructor, getter/setter, model field,
subscription management, and property-change listener implementation.

At the reviewed revision, `758215f`:

- Both production construction sites in `AssignmentSelectionPanel` use the
  constructor accepting GameData and Ship artwork rendering dependencies.
- Normal rendering supplies each editor with an immutable view and a callback
  associated with its fixed Assignment slot.
- The only external production call to the legacy setter passes `null` during
  disposal. The getter is used by a test inspecting the absence of a binding.
- The existing view-binding method still contains legacy unsubscribe logic.
- The workspace root already owns Assignment mutation and model subscriptions.

The deletion test favors contraction: removing the legacy path makes its
coordination disappear. Current callers need no replacement implementation
because the root already owns that behavior. The surviving interface gains
depth by exposing one ownership protocol; mutation and Solution invalidation
retain locality in the root.

## Change envelope and compatibility — Q1–Q3

- Preserve manual entry, Assignment and Event selection, displayed Solutions,
  Solution invalidation, deployment, and workspace-close behavior.
- Treat any newly discovered defect as a separate decision before including a
  behavioral correction in this migration.
- Retire the legacy mutable-Assignment interface completely in the same change.
  Do not retain deprecated forwarding methods or a compatibility adapter.
- Keep Assignment mutation and model subscriptions in `AdmiralPanel`.
  The editor owns controls, presentation, and reporting intended state.
- Preserve domain, scoring, Roster, persistence, and saved-Admiral formats.
  Numeric validation remains unchanged.

This completes the existing root-owned editing flow. It does not reopen
ADR-0001 or the GameData, GameData Refresh, or Ship Filter seam decisions.
No new domain term, glossary edit, or ADR is required.

## Surviving seam and synchronous edits — Q5

The owner-facing operations remain:

- construct the editor with GameData and Ship artwork rendering dependencies;
- `setAssignmentView(view, intent)` to project state and install its intent owner;
- `hasAssignmentView()` to inspect whether a view is bound;
- `setAssignmentSolution(solution)` to present a calculated Solution.

The existing Swing editor remains the presentation adapter. Keep the existing
`ShipIconFactory` rendering seam and its production and test adapters.

Each accepted control edit reports a complete immutable intended Assignment
state synchronously on Swing's event-dispatch thread. The root applies that
state, and synchronous model notifications reproject the authoritative view.
The editor does not commit an optimistic local copy or queue edits.

This timing is part of the interface: a later edit derives from the last
projected view. Deferring the owner's handling could cause it to derive from
outdated state. Document the synchronous owner contract rather than introducing
deferred handling in this migration.

Projection must emit no edit callbacks. Preserve the existing projection guard
and its suppression of synchronous control events, including the reasoning
comment explaining that suppression.

## Reversible unbinding and root disposal — Q4

`setAssignmentView(null, ...)` remains a valid, reversible unbind operation. It:

1. releases the retained Assignment view;
2. replaces the intent callback with the existing no-op behavior;
3. clears the retained Solution;
4. leaves the controls frozen at their current presentation.

Subsequent control events cannot reach the former owner. Rebinding installs the
new view and callback; later edits reach only that new owner. Do not add a
permanently disposed state to the editor.

Permanent disposal remains the workspace root's responsibility. Preserve its
existing ordering: mark the root disposed, detach its listeners, clear the
Assignment section's Solutions, unbind the editors, and then disable controls.
Unbinding before disabling is necessary because disabling controls can emit
synchronous events. Repeated root disposal remains safe.

`AssignmentSelectionPanel.dispose()` must use the surviving view-unbind path
instead of calling the retired mutable-model setter.

## Invalid inputs and failure behavior — Q7

- A non-null view requires a non-null intent callback. Reject a missing callback
  with `NullPointerException` before replacing any existing binding.
- Failed binding validation preserves the previous view and callback owner.
- A null view means unbind and ignores the callback argument, including a null
  callback.
- Preserve the constructor's non-null dependency requirements.
- Preserve the existing bound-view requirements for `clearAssignment` and
  `setAssignmentSolution`, including their `IllegalStateException` behavior.
- Do not introduce new numeric ranges or change AssignmentView validation.

## Event-thread enforcement — Q6 and Q12

Construction and the editor's declared mutation operations must reject calls
outside Swing's event-dispatch thread with `IllegalStateException`, before
changing editor state. Add an explicit constructor guard.

Preserve the existing guards on `setAssignmentView`, `setAssignmentSolution`,
and `clearAssignment`. Add guards to these currently unguarded public methods:

- `setShip1`;
- `setShip2`;
- `setShip3`;
- `clearSolutions`;
- `clearShips`.

Retain those five methods even though no external repository callers were found.
Their retirement would expand interface contraction beyond the agreed legacy
mutable-Assignment path. Retain the existing `clearAssignment` intent operation
as well.

The guarantee applies to declared editor operations, not inherited Swing
setters such as `setVisible`. Preserve the focus callback's existing
event-thread dispatch. Do not add guards indiscriminately to Swing callbacks or
override inherited setters to enforce a broader contract.

Explicit thread enforcement tightens the programmer-facing contract while
preserving the agreed user-visible behavior.

## Retired implementation and documentation

Remove from `AssignmentPanel`:

- the constructor accepting a mutable `Assignment`;
- `getAssignment()` and `setAssignment(Assignment)`;
- the retained mutable Assignment field;
- direct model subscription and unsubscription machinery;
- the legacy `propertyChange` implementation and its implemented listener role;
- legacy unsubscribe logic in `setAssignmentView`;
- imports made unused by those removals.

Control-level property-change listeners still implement real editor behavior;
they are not part of the retired model-listener path.

Preserve accurate comments. Remove legacy-specific Javadocs only with the code
they describe, and report those removals in the implementation summary. Update
surviving constructor and disposal documentation to describe the active view
contract. Add concise Javadoc to added or substantially rewritten methods,
including threading, lifetime, and failure semantics. Preserve comments
explaining projection suppression, disposal ordering, and intentional no-op
handling.

## Test surface — Q8 and Q9

Add focused editor tests through the surviving interface and observable control
interactions. They must demonstrate:

1. Projecting a view emits no intent callback.
2. A control edit reports the complete intended Assignment state.
3. Unbinding prevents subsequent control events from reaching the old owner.
4. Rebinding directs subsequent edits only to the new owner.
5. Invalid binding preserves the previous view and callback owner.
6. Construction and declared mutation operations reject off-thread calls before
   changing editor state, including the five additional guarded methods.

Retain workspace tests for manual edits and reference choices, correct Admiral
ownership, Solution invalidation, deployment, and disposal. They verify the
complete edit-to-model-to-Solution flow; focused editor tests verify the
binding and event contract without requiring that entire flow.

Replace the obsolete `getAssignment() == null` inspection with a focused
architecture assertion that the editor cannot retain or directly bind a mutable
`Assignment`. Behavioral coverage must continue proving that edits reach the
correct Admiral through the root. The architecture assertion must not prescribe
private helper names or Swing layout, or prohibit the root's legitimate model
ownership.

## Delivery and completion evidence — Q10 and Q11

This session delivers this design document. Implementation is a subsequent
step, following the confirmed contracts above.

The implementation is complete only when:

1. Focused editor contract tests pass.
2. Existing workspace coverage for editing, Solution invalidation, deployment,
   and disposal passes.
3. The architecture assertion prevents mutable-Assignment binding from returning.
4. `mvn clean test` passes on Java 25.
5. A focused Swing walkthrough covers Assignment/Event selection, manual entry,
   Solution display, and workspace closure.
6. `graphify update .` refreshes the graph after code changes.

Record automated checks separately from an actual manual walkthrough. State any
remaining verification gap explicitly rather than claiming completion from
automated coverage alone. Follow repository screenshot requirements if a
visible Swing change is proposed; such a change also requires the separate
behavior decision specified in Q1.

No implementation tests or manual verification are claimed by this document.
