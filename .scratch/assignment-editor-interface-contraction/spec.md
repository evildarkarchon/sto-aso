Status: ready-for-agent

# Finish the Assignment editor interface contraction

## Problem Statement

The Assignment editor still exposes public presentation operations and subclass-facing state that no production caller uses. Individual Ship-slot setters, clearing operations, public constants, protected fields, overridable initialization hooks, and a public `FocusListener` role reveal nearly as much presentation machinery as the implementation itself. Focused tests call these operations directly and therefore preserve distinctions that are not part of the application's actual Assignment-editing workflow.

This shallow interface makes the editor harder to understand and easier to misuse. It also weakens the ownership model already established for the Admiral workspace: the editor should project immutable Assignment and Solution state and synchronously report user intent, while the workspace root owns Assignment mutation, subscriptions, Solution invalidation, and disposal ordering.

## Solution

Complete the Assignment editor contraction without changing visible Swing behavior. Make the editor final and reduce its declared public interface to exactly four operations: construction, binding-state inspection, immutable Assignment projection, and complete Solution projection. Retire the six unused public Ship-slot and clearing operations without deprecated forwarding methods.

Move presentation helpers, focus handling, constants, initialization hooks, and retained state inside the implementation. Give focused tests only a narrow package-private seam for driving and reading the real numeric Swing controls, so control listeners and synchronous owner reprojection remain under test without exposing Assignment state, Solution state, Ship renderers, labels, or callback ownership.

Preserve the current synchronous event-thread contract, reversible unbinding, failure behavior, workspace-root ownership, disposal ordering, manual entry, Assignment and Event selection, Solution presentation, Solution invalidation, deployment, and workspace closure.

## User Stories

1. As a maintainer, I want the Assignment editor to expose only operations used by production callers, so that its interface communicates the real ownership protocol.
2. As a maintainer, I want the editor to be final, so that behavior cannot be altered through unsupported subclass hooks.
3. As a maintainer, I want individual Ship-slot presentation operations hidden, so that callers project complete Solutions instead of coordinating internal slots.
4. As a maintainer, I want Solution clearing hidden inside complete Solution projection, so that callers do not need to understand retained presentation state.
5. As a maintainer, I want Assignment clearing to remain owned by the workspace workflow, so that the editor does not expose a duplicate clearing path.
6. As a maintainer, I want no deprecated compatibility facade for the retired operations, so that the shallow interface disappears in one change.
7. As a maintainer, I want the editor's critical-rate constants to remain internal, so that implementation choices are not mistaken for reusable application contracts.
8. As a maintainer, I want unused presentation constants removed, so that dead interface surface cannot mislead future changes.
9. As a maintainer, I want focus-selection behavior implemented privately, so that the editor does not advertise a listener role to callers.
10. As a maintainer, I want initialization hooks internalized, so that construction has one supported implementation path.
11. As a maintainer, I want retained Assignment, Solution, callback, control, renderer, and label state hidden, so that callers cannot coordinate the implementation directly.
12. As a contributor, I want the surviving interface to use immutable Assignment projections, so that complete state crosses the seam.
13. As a contributor, I want the surviving interface to use complete Solution projections, so that Ship-slot presentation remains local to the editor.
14. As a contributor, I want binding-state inspection retained, so that the Assignment section can safely clear Solution presentation only for bound editors.
15. As a contributor, I want reversible unbinding retained, so that workspace disposal can release callback ownership without permanently disposing the editor.
16. As a contributor, I want a null Assignment projection to release the view, callback, and retained Solution while preserving frozen controls, so that existing disposal behavior remains stable.
17. As a contributor, I want rebinding to install only the new owner, so that later edits cannot reach a former workspace.
18. As a contributor, I want projection to emit no user intent, so that authoritative model state can be rendered without feedback loops.
19. As a contributor, I want accepted control edits delivered synchronously on Swing's event-dispatch thread, so that successive edits derive from the last authoritative projection.
20. As a contributor, I want the owner to apply and reproject accepted edits before returning, so that the editor never relies on an optimistic local Assignment copy.
21. As a contributor, I want invalid binding arguments rejected before state changes, so that the prior view and callback owner remain intact.
22. As a contributor, I want construction and surviving mutation operations to reject off-thread calls before changing state, so that Swing threading failures remain loud and deterministic.
23. As a contributor, I want constructor dependency validation preserved, so that missing GameData or Ship artwork rendering dependencies fail immediately.
24. As a contributor, I want focused tests to drive real numeric Swing controls, so that listener wiring and synchronous owner reprojection remain observable.
25. As a contributor, I want focused tests to avoid raw retained Solution and Ship-renderer state, so that internal presentation refactors do not rewrite behavior tests.
26. As a contributor, I want tests of retired helper distinctions removed, so that the test suite no longer preserves unsupported interface behavior.
27. As a contributor, I want workspace integration tests to continue covering manual edits, reference choices, Solution invalidation, deployment, and disposal, so that the complete Assignment workflow remains protected.
28. As a reviewer, I want an architecture guard proving the editor is final and the retired public operations are absent, so that the contraction cannot silently regress.
29. As a reviewer, I want architecture assertions to avoid private helper names and Swing layout details, so that implementation refactors remain possible.
30. As an application user, I want manual Assignment entry and Assignment and Event selection to behave exactly as before, so that the architecture change is invisible during normal use.
31. As an application user, I want calculated Solutions and their assigned Ship cards to render exactly as before, so that planning behavior is unchanged.
32. As an application user, I want clearing Assignments, changing the Assignment count, and deploying a Solution to behave exactly as before, so that workspace actions retain their current semantics.
33. As an application user, I want closing an Admiral workspace to release its editor callbacks safely, so that disposed workspaces cannot receive later edits.
34. As a future agent, I want the Assignment editor design record updated with this contraction, so that a later architecture review does not reintroduce the retired surface.
35. As a future agent, I want one small, explicit interface and a narrow internal test seam, so that the module is easy to navigate without learning presentation implementation details.

## Implementation Decisions

- The Assignment editor becomes a final Swing module.
- Its declared public interface contains exactly four operations: construction, binding-state inspection, immutable Assignment projection with its synchronous intent owner, and complete Solution projection.
- Retire the public operations that individually present the first, second, or third Ship, clear an Assignment, clear Solutions, or clear Ship cards.
- Do not retain deprecated methods, forwarding methods, compatibility adapters, or alternate construction paths for the retired interface.
- The complete Solution projection remains responsible for presenting or clearing retained Solution state and all three Ship cards. Any helper operations needed to do that remain private implementation.
- Assignment clearing remains part of the existing workspace-root workflow. The unused editor-level clearing operation is removed rather than made private or replaced.
- Keep Assignment mutation, model subscriptions, Solution invalidation, and permanent disposal in the Admiral workspace root.
- Keep the editor responsible for Swing controls, presentation, reversible binding, and synchronous reporting of complete intended Assignment state.
- Preserve the existing owner-facing synchronous contract: an accepted control edit reports one complete immutable intended Assignment state on Swing's event-dispatch thread; the owner applies and reprojects authoritative state before returning.
- Preserve the projection guard that prevents programmatic control updates from echoing user intent. Preserve the reasoning comment that explains this ordering constraint.
- Preserve reversible unbinding. A null Assignment projection releases the retained view, callback owner, and Solution while leaving controls frozen. Rebinding later is supported.
- Preserve the existing failure order: invalid dependencies, missing callbacks, unbound Solution projection, and off-thread calls fail before partial state changes.
- Preserve the established workspace disposal order, including unbinding before recursively disabling controls because disabling can emit synchronous Swing events.
- Remove the unused public color constant. Make the minimum and maximum critical-chance values private implementation constants.
- Remove the editor's public `FocusListener` role. Preserve the same focus-gained select-all behavior through a private listener implementation; focus-lost remains an intentional no-op inside that implementation.
- Make current protected controls, renderers, labels, retained state, and formatting state private except for the minimum numeric-control access required by the package-private test seam.
- Internalize the design-time and runtime initialization hooks. Preserve existing design-time versus runtime behavior; do not turn them into subclass extension points.
- The internal test seam exposes only enough access to drive and read the seven numeric Assignment and Event controls through their real Swing values. It does not expose retained Assignment state, retained Solution state, Ship renderers, labels, callback ownership, or direct intent publication.
- Preserve the existing production `ShipIconFactory` seam and its production and test adapters. No new adapter is introduced for this contraction.
- Preserve the inherited Swing panel interface used for visibility, sizing, enablement, containment, and normal Swing behavior; only the editor's declared interface is contracted.
- Strengthen the existing architecture guard to assert finality and absence of the six retired declared public operations. Do not prescribe private helper names, field names, layout, or a specific internal listener class.
- Update the existing Assignment editor design record to supersede its earlier decision to retain the six operations. Record that the earlier retention was a change-envelope limit rather than a load-bearing architectural constraint.
- No domain glossary change or ADR is required. The existing Assignment and Solution terms remain precise, the change is readily reversible, and it does not reopen accepted data-directory or build-system decisions.
- Preserve accurate comments. Remove or rewrite comments only with the code or contract they describe, and report those changes during implementation. Add concise Javadoc to substantially rewritten surviving methods and document event-thread and lifetime contracts.

## Testing Decisions

- The primary test surface is the four-operation declared public interface plus observable Swing control and card presentation. Tests should describe behavior a caller or user can observe, not private helper distinctions.
- Use the narrow package-private numeric-control seam only to generate and inspect real Swing control changes. Do not call the intent callback directly from tests and do not expose internal state merely for assertions.
- Preserve a focused test proving construction rejects off-thread use. Remove the anonymous subclass probe because the final editor has no subclass seam; the surviving assertion covers the declared constructor contract.
- Preserve off-thread tests for binding, unbinding, and complete Solution projection. Remove off-thread cases for the six retired operations.
- Preserve the test proving projection emits no edit callback.
- Preserve the test proving one control edit reports the complete intended Assignment state synchronously and uses the owner's authoritative reprojection.
- Preserve the test proving an owner that does not reproject leaves later edits based on the last authoritative view.
- Preserve tests proving unbinding prevents callbacks to the old owner and rebinding sends later edits only to the new owner.
- Preserve the test proving invalid callback binding leaves the previous view, visible controls, and callback owner intact.
- Preserve constructor dependency validation and the unbound rejection for complete Solution projection.
- Replace direct tests of individual Ship-slot setters and the distinction between clearing Ships versus clearing Solutions with observable tests of complete Solution projection using a Solution and null.
- Replace direct retained-Solution assertions with presentation snapshots, binding-state inspection, callback observations, and documented failure outcomes.
- Replace direct Ship-renderer assertions with recursive visible-presentation assertions that do not depend on component positions or layout.
- Retain the existing architecture test that forbids mutable Assignment retention or direct binding in the editor and forbids the model `PropertyChangeListener` role.
- Add focused architecture assertions that the editor is final and none of the six retired method names remain declared public operations. The guard must not reject private helpers with replaceable names or inspect Swing layout.
- Retain Admiral workspace integration coverage for manual edits, Assignment and Event selection, correct Admiral ownership, Solution invalidation, deployment, clearing Assignments, Assignment-count changes, disposal, and repeated disposal.
- Use existing Assignment editor focused tests, Admiral workspace integration tests, and architecture tests as prior art. Do not add a new external seam solely for testing.
- Run the focused Assignment editor test class during iteration, followed by the focused architecture and Admiral workspace test classes.
- Completion requires the full clean Gradle build on the installed JDK 25, including all JUnit and architecture verification.
- Perform a focused Swing walkthrough covering Assignment and Event selection, manual numeric entry, Solution display and clearing, Assignment clearing, and workspace closure. Record this separately from automated results.
- No before-and-after screenshot is required because the specification forbids a visible Swing change. If implementation changes visible layout or interaction, stop and reopen the behavior decision before proceeding.

## Out of Scope

- Changing visible Swing layout, labels, colors, spacing, control order, card rendering, or focus behavior.
- Adding, removing, or changing Assignment fields, numeric ranges, validation rules, Event semantics, or critical-rate calculations.
- Changing the synchronous edit contract, introducing deferred callbacks, or keeping an optimistic Assignment copy in the editor.
- Moving Assignment mutation, model subscriptions, Solution invalidation, deployment, or permanent disposal out of the Admiral workspace root.
- Making complete Assignment edits atomic in the Admiral domain module; that is a separate deepening opportunity.
- Changing Solver behavior, Solution scoring, Roster behavior, Ship identity, GameData, GameData Refresh, Icon Cache, or Ship Filter behavior.
- Changing persistence, Admirals XML compatibility, CSV formats, digest manifests, or saved-Admiral state.
- Contracting unrelated Swing panels or their fields and methods.
- Replacing Swing, redesigning the Assignment workflow, or introducing a new presentation framework.
- Removing or restricting inherited Swing panel operations.
- Adding a public test interface, test-only production adapter, or general control registry.
- Preserving source compatibility for external code that called the six retired operations or subclassed the editor.
- Creating a new domain term, editing the domain glossary, or creating an ADR.
- Creating implementation tickets as part of this specification publication.

## Further Notes

- Repository search found no production caller of the six retired operations. Their callers are focused tests that currently encode presentation implementation.
- The only production owner constructs the editor and uses the four surviving operations. It also relies on inherited panel visibility, sizing, and enablement behavior, which remains unchanged.
- The prior Assignment editor design deliberately retained five presentation methods and the editor-level clearing operation to limit the earlier migration's scope. This specification supersedes that narrow retention decision after a second architecture review and confirmed grilling session.
- The deletion test is decisive: removing the six public operations moves no complexity to callers. Necessary presentation behavior remains inside complete Assignment and Solution projection.
- Two adapters already justify the Ship artwork seam: the production renderer and deterministic test renderer remain unchanged.
- The specification introduces no data-format, XML-compatibility, package-direction, or ADR effect.
- The specification is fully decided and labeled `ready-for-agent`; implementation may proceed without another product interview unless code evidence contradicts a stated contract.
