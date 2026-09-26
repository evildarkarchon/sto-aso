Status: ready-for-agent

# Finish the residual Ship Filter retirement

## Problem Statement

The application has completed its migration to the deep Ship Filter module, but four obsolete production types remain. Three form an unreachable table-based filtering stack with a large mutable interface that duplicates faction, role, tier, rarity, and ordering behavior already owned by the canonical Ship Filter module. The fourth is a dialog-selection helper with no production caller; only its own test retains it, while the live named Ship Filter selection paths already own and test the same acceptance and cancellation contract.

These dead types make the architecture harder to navigate. A maintainer or coding agent can still discover two apparent Ship Filter implementations and may edit or reuse the obsolete one. The duplicate dialog helper similarly presents a false seam that is not part of the running application. Keeping either path weakens locality, obscures the supported interface, and risks future work reviving behavior that the completed Ship Filter migration intentionally replaced.

## Solution

Finish the retirement by deleting the unreachable table-based Ship Filter types, the unused dialog-selection helper, and the test that exists only for that dead helper. Do not retain deprecated forwarding types, compatibility adapters, or alternate implementations.

Preserve all live Ship Filter behavior and Swing presentation unchanged. Strengthen the existing architecture guard so it rejects any future production declaration of the four retired types, regardless of filename or package. Keep the live Ship Filter view tests as the behavioral test surface for accepted, cancelled, closed, and empty selection outcomes.

Record the residual retirement as a dated follow-up to the existing Ship Filter design and verification records. Preserve historical issue scope, commands, test counts, screenshots, and verification evidence as historical facts rather than rewriting them to imply that they covered this later cleanup.

## User Stories

1. As a maintainer, I want the unreachable table-based Ship Filter implementation removed, so that the repository presents one supported Ship Filter module.
2. As a maintainer, I want the obsolete mutable filtering booleans removed, so that callers cannot rediscover a shallow interface for faction, role, tier, and rarity criteria.
3. As a maintainer, I want the unused Ship table model removed, so that dead table presentation is not mistaken for a current Ship Filter path.
4. As a maintainer, I want the unused integer comparator removed, so that obsolete ordering machinery does not survive without a caller.
5. As a maintainer, I want the residual models package removed when it becomes empty, so that package structure reflects the running application.
6. As a maintainer, I want the unused dialog-selection helper removed, so that modal outcome behavior has one owner.
7. As a maintainer, I want the duplicate dialog-helper test removed, so that tests do not preserve an interface absent from production behavior.
8. As a maintainer, I want no deprecated forwarding types, so that the retired interface disappears in one change.
9. As a maintainer, I want no compatibility adapters for unreachable types, so that the canonical Ship Filter seam remains unambiguous.
10. As a contributor, I want Ship Filter visibility and ordering behavior to remain unchanged, so that this retirement is architecture-only.
11. As a contributor, I want the named Ship Filter selection paths to retain their existing acceptance behavior, so that accepted selections remain in visible order with exact entry identities.
12. As a contributor, I want cancellation and window closure to continue returning no selection, so that modal outcomes remain stable.
13. As a contributor, I want accepting with no selection to continue returning an empty immutable result, so that callers retain the current contract.
14. As a contributor, I want returned selection collections to remain immutable, so that callers cannot mutate a completed dialog outcome.
15. As a contributor, I want reusable Ship, One-Time Ship, and Roster-card selection paths to retain the same dialog behavior, so that no path is accidentally excluded from coverage.
16. As a reviewer, I want an architecture guard that rejects the four retired declarations anywhere in production source, so that renaming or relocating a file cannot revive the old interface.
17. As a reviewer, I want the architecture guard observed failing while the declarations still exist, so that the new assertion is proven to exercise the intended condition.
18. As a reviewer, I want focused tests at the live Ship Filter interface, so that behavior coverage does not depend on deleted helpers.
19. As a reviewer, I want a clean full build after retirement, so that source deletion cannot leave unresolved references or hidden build inputs.
20. As a future agent, I want the Ship Filter design record to mention the residual cleanup, so that later architecture reviews do not propose or restore the obsolete stack.
21. As a future agent, I want the verification record to distinguish the original migration from this follow-up, so that historical evidence remains trustworthy.
22. As a future agent, I want the verification record to state that the residual models package is gone, so that the supported module location is easy to identify.
23. As a future agent, I want the repository code graph refreshed after deletion, so that architecture exploration cannot return stale retired symbols.
24. As an application user, I want Ship filtering, ordering, selection, and Starship Trait presentation to look and behave exactly as before, so that internal cleanup is invisible.
25. As an application user, I want no layout, label, focus, scrolling, or dialog changes, so that the retirement introduces no presentation regression.
26. As a project owner, I want the deletion test to remove complexity rather than move it, so that this change genuinely deepens the existing module.
27. As a project owner, I want the work limited to residual retirement and enforcement, so that unrelated Ship Filter improvements remain separately reviewable.
28. As a project owner, I want no domain glossary or ADR change, so that architecture documentation changes only where the existing Ship Filter records need a dated follow-up.

## Implementation Decisions

- Delete the obsolete `ShipRowFilter`, `ShipTableModel`, and `IntegerComparator` production types. They have no production or test callers outside their own unreachable cluster.
- Delete the obsolete `DialogSelections` production type. It has no production caller, and its behavior is already owned by the live named Ship Filter selection paths.
- Delete the test dedicated exclusively to `DialogSelections`. Do not replace it with another helper-level test.
- Remove the residual models package after its three files are deleted. Do not preserve an empty package marker or compatibility facade.
- Do not retain deprecated types, forwarding types, aliases, adapters, or alternate declarations for any retired type.
- Treat the current `ui.shipfilter` module as the sole Ship Filter module. Do not modify its production implementation as part of this retirement.
- Preserve the existing modal outcome behavior in the live Ship Filter selection paths: explicit acceptance returns the immutable visible selection; cancellation, window close, and empty acceptance return an empty immutable selection.
- Preserve all current Ship Filter criteria, profiles, ordering, identity retention, renderers, scrolling, dialog layout, and optional Ship details behavior.
- Extend the existing retired-Ship-Filter architecture guard rather than creating a second architecture test seam.
- Make the guard reject declarations of all four retired types anywhere in production source. The assertion must not depend solely on filenames or their former packages.
- Broaden the architecture guard's name and documentation so it accurately covers residual panels, models, filters, comparators, and dialog helpers.
- Preserve the special exclusion for the established Ship Selection string-resource namespace; it is unrelated to the retired production declarations.
- Add a dated residual-retirement note to the Ship Filter seam design record. Do not rewrite the original issue's scope as if it included this later cleanup.
- Append new follow-up evidence to the Ship Filter retirement verification record, including the four residual declarations, removal of the obsolete models package, retained live dialog coverage, and current verification commands.
- Preserve historical Maven results, test counts, graph-refresh commands, visual baselines, and manual-verification limitations as historical evidence.
- No domain glossary change is required. The existing Ship Filter, Ship, Roster, One-Time Ship, and Starship Trait terms remain sufficient.
- No ADR is required. The cleanup completes an already accepted and implemented module migration without changing a durable cross-cutting decision.
- Preserve accurate comments in surviving files. Comments and Javadocs belonging to deleted types and their deleted test are removed only with that code and must be reported in the implementation summary.

## Testing Decisions

- The highest test seam for retirement is the existing architecture declaration guard. It should express that the four obsolete production types may not exist anywhere, without asserting replaceable implementation details of the live Ship Filter module.
- The highest behavioral test seam is the live named Ship Filter view interface. Do not retain or recreate a test seam for the dead dialog helper.
- Before deleting production declarations, add the four type names to the architecture guard and observe the focused architecture test fail. This demonstrates that the guard detects the current forbidden state.
- After deletion, run the focused architecture test and require it to pass.
- Run the focused Ship Filter view test to retain coverage for explicit acceptance, cancellation, window close, empty acceptance, visible order, exact entry identity, and immutable results.
- Ensure all three named selection paths remain covered: reusable Ships, One-Time Ships, and Roster cards.
- Do not add direct tests for the retired table model, row filter, comparator, or dialog helper. Their absence is the contract.
- Use the existing Ship Filter architecture assertions and Ship Filter view dialog-contract tests as prior art. Do not introduce a new seam solely for testing.
- Run the full clean Gradle build on the installed JDK 25 after focused verification. Completion requires all compilation, JUnit, and architecture checks to pass.
- Refresh the repository CodeGraph index after source deletion so subsequent architecture exploration reflects the new source graph.
- A manual Swing walkthrough and new screenshots are not required because the specification forbids changes to reachable production behavior or presentation.
- If implementation unexpectedly changes live `ui.shipfilter` production code, stop and reopen the behavior and visual-verification decisions before proceeding.
- Record automated verification separately from historical evidence. Do not claim that earlier test counts or screenshots verified this later residual cleanup.

## Out of Scope

- Changing the live Ship Filter implementation, interface, criteria, profiles, ordering, duplicate handling, selection reconciliation, or event publication.
- Moving Starship Trait eligibility from Swing presentation into the headless Ship Filter module.
- Changing Ship artwork rendering, Icon Cache behavior, or Icon Cache Refresh architecture.
- Changing Assignment editing, Assignment mutation, Admiral workspace ownership, Solver behavior, or Solution scoring.
- Changing any Swing layout, label, color, spacing, focus behavior, scrolling behavior, renderer, modal dialog, or Ship details presentation.
- Adding a new Ship Filter dimension, ordering option, entry kind, adapter registration seam, or compatibility interface.
- Preserving source compatibility for external code that constructed or referenced the retired public table types.
- Rewriting the original Ship Filter issue scope, historical verification commands, test counts, visual baselines, or manual-walkthrough status.
- Changing GameData, GameData Refresh, Roster, Admirals persistence, XML compatibility, CSV formats, digest manifests, or saved-Admiral state.
- Creating a new domain term, editing `CONTEXT.md`, or creating an ADR.
- Creating implementation tickets as part of this specification publication.

## Further Notes

- Repository CodeGraph and textual-reference audits found no live caller for `ShipRowFilter`, `ShipTableModel`, or `IntegerComparator`. Together they form the entire obsolete models package.
- `DialogSelections` has no production caller. Its only callers are methods in the dedicated test that exists solely for that dead helper.
- The live Ship Filter view tests already exercise the modal outcome contract for reusable Ship, One-Time Ship, and Roster-card selection paths, including acceptance, cancellation, close, empty acceptance, ordering, identity, and immutability.
- The deletion test is decisive: removing the four production types makes their interface and implementation disappear without moving complexity to callers.
- The current GameData Refresh module, Assignment editor, and canonical Ship Filter module remain unchanged by this specification.
- The specification is fully decided and labeled `ready-for-agent`; implementation may proceed without another product interview unless code evidence contradicts a stated contract.
