Status: ready-for-agent

# Deepen the Ship Artwork lifecycle

## Problem Statement

Ship Artwork currently works as a collection of loosely coordinated cache,
factory, loader, bootstrap, worker and shutdown behaviors. A user can see
generic artwork even when specific artwork is available later, because completed
background acquisition does not repaint already-visible Ship cards. Existing
artwork is not reliably refreshed: the application marks the whole archive
fresh when work is merely scheduled, then skips entries already present. Failed
work may therefore suppress another attempt for seven days.

The current implementation also permits duplicate concurrent downloads, relies
on process-global state from background work, keys composed pixels only by an
image filename, and exposes storage operations to callers. That filename does
not capture the faction, role, rarity, source-image identity or composition
recipe that produced the visible artwork. A caller must understand nearly the
entire lifecycle to obtain one image.

Users also have existing locally generated `icons.zip` archives that cannot be
safely overwritten with a new metadata format. The project needs a reversible,
inspectable transition that preserves useful legacy pixels, reports unknown
entries, permits explicit online refresh, and cannot accidentally mutate an
unintended data directory.

## Solution

Introduce one application-owned, deep Ship Artwork module. A caller supplies a
canonical Ship and chooses specific or generic presentation, then immediately
receives a stable, read-only Swing image handle. The module hides composition,
fallback selection, bundled-image lookup, remote acquisition, request
coalescing, live repaint, freshness, retry, persistence, recovery and migration
behind that small interface.

Specific presentation immediately shows current, bundled, stale last-known-good
or generic Ship Artwork in that order. Missing or stale remote artwork is filled
asynchronously. When fresh pixels arrive, the existing handle updates on
Swing's event thread and repaints its visible owner without changing Ship Filter
state or selection.

Persist new state in a separate versioned archive. Treat legacy `icons.zip` as
read-only migration input and retain it for rollback. Provide automatic lazy
migration for ordinary users and headless inspect, migrate, verify and explicit
cleanup operations for maintainers. Offline operation is the default; GitHub
access requires an explicit online-refresh option.

## User Stories

1. As an application user, I want Ship cards to show artwork immediately, so that a slow or unavailable network never blocks the interface.
2. As an application user, I want a specific Ship image when one is available, so that owned reusable Ships remain visually recognizable.
3. As an application user, I want generic Ship Artwork when a specific image is unavailable, so that every Ship remains usable and visually classified.
4. As an application user, I want One-Time Ships to retain their generic presentation, so that current Roster semantics remain recognizable.
5. As an application user, I want stale but valid artwork to remain visible during refresh, so that a transient failure does not downgrade my display.
6. As an application user, I want freshly acquired artwork to appear in already-open windows, so that I do not need to restart or reopen a view.
7. As an application user, I want live artwork replacement to preserve Ship Filter criteria, so that background work does not reset my view.
8. As an application user, I want live artwork replacement to preserve selected Ships and Roster cards, so that background work does not disrupt an active choice.
9. As an application user, I want artwork failures to remain nonfatal, so that optional images cannot prevent application startup.
10. As an application user, I want artwork failures reported without modal dialogs, so that background problems do not interrupt normal work.
11. As an application user, I want repeated requests for one image to share one download, so that opening several views does not waste bandwidth.
12. As an application user, I want failed artwork to retry later, so that a transient GitHub failure can recover while the application remains open.
13. As an application user, I want failed acquisition to use increasing backoff, so that repainting cannot create a tight network loop.
14. As an application user, I want the next launch to retry previously failed artwork, so that in-process backoff never becomes permanent.
15. As an application user, I want application shutdown to finish promptly, so that an unresponsive network cannot hang exit.
16. As an application user, I want artwork completed before shutdown to be retained, so that successful work is not needlessly repeated next launch.
17. As an application user, I want an unreadable new archive to recover automatically, so that derived-state corruption does not make the application unusable.
18. As an application user, I want my legacy archive preserved during migration, so that I can return to an older application version safely.
19. As an application user, I want migration to reuse recognizable legacy pixels as last-known-good artwork, so that transition does not unnecessarily discard useful images.
20. As a maintainer, I want callers to request Ship Artwork rather than operate an Icon Cache, so that the interface expresses the presentation need instead of storage mechanics.
21. As a maintainer, I want the module interface to accept canonical Ships, so that identity, source lookup and composition facts cannot disagree.
22. As a maintainer, I want generic and specific presentation to be named choices, so that an ownership boolean cannot obscure presentation meaning.
23. As a maintainer, I want one application-owned module instance, so that memory state, acquisition, persistence and lifetime have one owner.
24. As a maintainer, I want cache keys hidden inside the module, so that callers cannot couple themselves to archive layout or recipe versions.
25. As a maintainer, I want persisted identity to include source, canonical presentation facts and recipe version, so that stale composite pixels are not reused incorrectly.
26. As a maintainer, I want freshness recorded per remote image, so that partial success cannot mark failed or unrelated artwork current.
27. As a maintainer, I want bundled artwork tied to the application version, so that packaged resources do not require remote freshness checks.
28. As a maintainer, I want composition assets to be required bundled resources, so that generic rendering cannot unexpectedly depend on a network request.
29. As a maintainer, I want missing composition assets to fail module construction loudly, so that a broken distribution is diagnosed rather than silently misrendered.
30. As a maintainer, I want GitHub acquisition behind an internal port, so that external behavior is deterministic in tests without exposing transport choice to callers.
31. As a maintainer, I want filesystem work tested against temporary directories, so that a broad filesystem abstraction does not widen the interface.
32. As a maintainer, I want time, delayed work and Swing dispatch controllable internally in tests, so that backoff, debounce and event ordering are deterministic.
33. As a maintainer, I want a stable live image handle, so that every Swing renderer gets live replacement without registering its own listener.
34. As a maintainer, I want live handles to track paint owners weakly, so that hidden or disposed windows are not retained.
35. As a maintainer, I want repaint requests coalesced on Swing's event thread, so that one artwork update cannot flood the event queue.
36. As a maintainer, I want unchanged refreshed pixels to avoid a false repaint event, so that freshness updates do not cause unnecessary presentation work.
37. As a maintainer, I want at most three remote acquisitions at once, so that background artwork work remains bounded.
38. As a maintainer, I want response time, size, origin, content and dimension limits, so that malformed or excessive remote content is rejected safely.
39. As a maintainer, I want successful on-demand fills persisted after a short debounce, so that one archive rewrite can cover several nearby changes.
40. As a maintainer, I want a maximum persistence delay, so that continuous acquisition cannot postpone durable state indefinitely.
41. As a maintainer, I want startup prewarming persisted once per batch, so that initial acquisition does not rewrite the archive per Ship.
42. As a maintainer, I want archive installation to use completed replacement files, so that a failed write cannot publish a partial archive.
43. As a maintainer, I want the prior v2 archive preserved when replacement fails, so that recoverable persistence errors do not destroy working state.
44. As a maintainer, I want corrupt v2 state quarantined when possible, so that diagnostic evidence survives recovery.
45. As a maintainer, I want recovery to continue if quarantine fails, so that optional artwork cannot become a startup requirement.
46. As an operator, I want to inspect legacy and v2 archives without writing, so that I can understand a user's transition state safely.
47. As an operator, I want offline migration by default, so that inspecting or converting an archive never implies network permission.
48. As an operator, I want an explicit online-refresh option, so that remote acquisition is visible and intentional.
49. As an operator, I want migration to report matched, migrated, unreadable and unmatched entries, so that no artwork silently disappears.
50. As an operator, I want unknown legacy entries left in the untouched archive, so that omitted derived state remains recoverable.
51. As an operator, I want to verify every v2 entry, digest, identity and metadata relationship without writing, so that migration results can be audited.
52. As an operator, I want cleanup to require successful v2 verification and explicit confirmation, so that rollback state is not deleted accidentally.
53. As an operator, I want every tool operation to require an explicit data directory, so that the command cannot mutate the wrong user state.
54. As an operator, I want human-readable output by default, so that interactive use is straightforward.
55. As an operator, I want optional JSON output, so that migration and verification can be automated reliably.
56. As an operator, I want distinct exit-status categories, so that scripts can distinguish invalid arguments, verification findings and operational failure.
57. As a contributor, I want application and tooling migration to share one implementation, so that identity and archive rules cannot drift.
58. As a contributor, I want tests to exercise the highest Ship Artwork seam, so that internal refactoring does not rewrite behavior tests.
59. As a contributor, I want remote success, failure, delay and interruption scripted through a deterministic adapter, so that network cases remain fast and repeatable.
60. As a contributor, I want live replacement tested on Swing's event thread, so that thread confinement remains an observable contract.
61. As a contributor, I want visual baselines retained for every Ship presentation, so that architecture work does not change established appearance.
62. As a contributor, I want old loader, cache and factory tests replaced rather than layered beneath new tests, so that the retired implementation is not preserved by its test suite.
63. As a reviewer, I want architecture checks that reject retired icon modules, so that forwarding or alternate implementations cannot silently return.
64. As a reviewer, I want architecture checks that permit internal implementation changes, so that they enforce the seam without freezing private class names.
65. As a reviewer, I want a clean Java 25 Gradle build and focused offline tool verification, so that the migration is proven across application and operator paths.
66. As a reviewer, I want an actual manual online refresh recorded separately, so that automated tests are not misrepresented as network verification.
67. As a future agent, I want Ship Artwork and Icon Cache defined separately in the glossary, so that later work preserves the caller-facing concept and internal persisted state.
68. As a future agent, I want the stable live image decision recorded in an ADR, so that its Swing-specific trade-off is not "simplified" back into shallow listeners or cache access.
69. As a future agent, I want a complete seam-design record, so that later implementation and review can recover every accepted behavior and constraint.
70. As a project owner, I want the migration delivered through green stages but completed without compatibility interfaces, so that the final architecture has one supported path.

## Implementation Decisions

- Add one application-owned Ship Artwork module under the Swing presentation area. Ship Artwork is the external concept; Icon Cache is internal persisted state.
- Open the module after GameData loads and before any Swing frame is constructed. Supply the resolved data directory, loaded GameData and current-Roster Ship types at creation.
- Keep the application interface to creation, immediate artwork lookup and idempotent close. Batch, cache, acquisition, migration and listener protocols are not part of the UI interface.
- Artwork lookup accepts only canonical Ships belonging to the supplied GameData. Null and foreign values are programming errors rejected before state changes or background work.
- Replace the existing five-argument artwork request with a canonical Ship and a named presentation choice: specific or generic.
- Generic presentation uses canonical faction, role and rarity facts and never initiates network work.
- Specific presentation immediately chooses current versioned artwork, freshly composed bundled artwork, stale last-known-good artwork or generic artwork in that order.
- Return a non-null, fixed-size, stable, read-only Swing image handle immediately. The caller performs no waiting and learns no future, cache key or listener protocol.
- The stable handle privately swaps its pixel delegate on Swing's event thread. It weakly tracks useful paint owners and coalesces targeted repaint work without changing models, filters or selection.
- One-Time Ship paths retain generic presentation even when specific artwork exists. Reusable Roster cards use specific presentation with fallback.
- Startup prewarms current-Roster Ship types. Specific requests later trigger on-demand acquisition when artwork is missing or stale.
- Coalesce startup and on-demand requests by internal remote image identity. Permit no more than three active remote acquisitions.
- Track successful freshness per remote image. Preserve the seven-day freshness cadence. Do not advance freshness when work is scheduled, fails, is cancelled or is interrupted.
- Apply one-, five- and thirty-minute in-process backoff after repeated failures for one image. Success resets backoff. Explicit online refresh and the next launch bypass it.
- Retain last-known-good artwork after refresh failure. Use generic artwork only when no valid specific artwork is available.
- Log one diagnostic per coalesced failure and do not display modal artwork errors.
- Treat GitHub as a true external dependency behind a private acquisition port with production, scripted-test and offline-enforcement adapters.
- Keep the remote origin fixed. Use HTTPS with a five-second connection timeout, fifteen-second request timeout, two-MiB response limit and 2048-by-2048 decoded dimension limit.
- Permit redirects only between allowlisted GitHub HTTPS origins. Reject invalid content types, incomplete decodes, oversized responses, invalid dimensions and unexpected redirects.
- Treat faction backgrounds and role and rarity frames as required bundled composition resources. Module construction fails loudly when one is missing or unreadable.
- Keep cache identity internal and versioned. Include source-image identity, every canonical Ship fact used by composition and the composition recipe version.
- Store v2 state separately from legacy `icons.zip`. The legacy archive is read-only migration input and remains available for rollback.
- Record archive schema and recipe versions, per-entry identity, successful remote freshness and integrity evidence in the v2 format.
- Load recognizable legacy entries as stale last-known-good artwork. Legacy pixels alone never satisfy a current versioned identity.
- Report and omit unknown, removed, unreadable or unmappable legacy entries from v2 while retaining them in the untouched legacy archive.
- Persist on-demand successes two seconds after the last successful fill, with a thirty-second maximum delay during continuous work.
- Persist once after startup prewarming and perform a final flush at close.
- Write complete replacement archives and prefer atomic installation with completed-file replacement fallback. A failed installation preserves the prior v2 archive.
- Quarantine corrupt v2 state under a recovery name when possible, then continue with legacy or generic artwork and rebuild. If quarantine fails, leave the unreadable archive untouched, log the failure and continue in memory.
- Close stops accepting new acquisition, allows a short bounded grace period, cancels remaining work and flushes completed artwork. Network work cannot hang application exit.
- Preserve interruption and leave cancelled work due at the next launch. Close is idempotent.
- Provide automatic lazy migration in the application and a headless operator adapter using the same implementation.
- Expose the operator adapter through a Gradle Java execution task. Require an explicit data-directory option for every invocation; do not reuse application directory inference.
- Support read-only inspect, offline-by-default migrate, explicit online-refresh migration, read-only verify and explicit legacy cleanup operations.
- Require successful v2 verification plus an explicit confirmation flag before legacy cleanup. Resolve and validate the exact target before deletion.
- Produce concise human output by default and optional JSON from the same structured outcome.
- Use distinct nonzero exit-status categories for invalid arguments, verification findings and operational failures. Unmatched legacy entries are findings.
- Delete the existing artwork factory interface, generic factory, production factory, Icon Cache module and background icon loader after every caller migrates.
- Remove icon-download scheduling and eligibility from bootstrap and the general Swing worker executor.
- Remove Icon Cache access from process-global application state. Pass the one Ship Artwork instance through ownership seams.
- Replace shutdown Icon Cache saving with Ship Artwork close.
- Do not retain deprecated methods, forwarding interfaces, compatibility adapters or alternate artwork implementations in the completed migration.
- A temporary adapter may exist only inside green migration stages and must be deleted before the work is complete.
- Preserve ADR-0001: application startup alone resolves the data directory, while the headless tool requires an explicit target.
- Preserve the accepted stable live-handle ADR and the Ship Artwork seam-design record.
- Preserve accurate comments. Remove comments only with deleted code or when the changed contract makes them misleading, and report such changes during implementation. Add concise Javadocs for new or substantially rewritten methods, especially threading, lifetime and failure contracts.

## Testing Decisions

- The primary application test seam is Ship Artwork creation, lookup and close. Tests assert observable image, repaint, persistence and failure behavior without inspecting internal cache maps, keys, workers or classes.
- The primary operator test seam is the headless tooling adapter. It delegates to the same implementation and is tested through commands, structured reports, writes and exit categories.
- Use small GameData builders so every requested Ship is canonical to the tested module.
- Use deterministic scripted acquisition adapters for successful bytes, malformed bytes, delay, failure, redirects, interruption and cancellation.
- Use real temporary directories and fixture archives for local persistence and migration. Keep only a narrow internal fault adapter for replacement failures that cannot be induced reliably.
- Use deterministic internal time and scheduling adapters to test seven-day freshness, one/five/thirty-minute backoff, two-second debounce, thirty-second maximum delay and bounded shutdown without wall-clock sleeps.
- Verify that lookup immediately returns fixed-size generic artwork when no specific pixels exist.
- Verify that bundled specific artwork is composed without invoking the remote adapter.
- Verify that repeated lookup returns the same stable image-handle identity.
- Verify that callers cannot mutate the stable handle's internal presentation state.
- Verify that remote success replaces pixels and triggers repaint on Swing's event thread.
- Verify that live repaint does not publish list-model events, rebuild a Ship Filter or change selected identities.
- Verify that disposed or unreachable Swing owners can be reclaimed and receive no retained strong reference from artwork handles.
- Verify that unchanged refreshed pixels update freshness without unnecessary repaint.
- Verify that startup prewarming and on-demand requests for one image produce one acquisition.
- Verify the three-request concurrency limit and continued progress when an attempt completes or fails.
- Verify that connection timeout, request timeout, response size, redirect origin, content type, decode completeness and dimension limits become nonfatal acquisition failures.
- Verify that one coalesced failure produces one diagnostic, retains last-known-good or generic artwork and activates the correct backoff step.
- Verify that success, explicit online refresh and process restart reset or bypass backoff as specified.
- Verify that freshness advances only for a successfully acquired image and never for merely scheduled, failed, cancelled or interrupted work.
- Verify startup-batch, debounced, maximum-delay and final-flush persistence through restart-visible outcomes.
- Verify close rejects new acquisition, grants bounded completion, cancels remaining work, preserves interruption and returns without a network hang.
- Verify complete replacement, atomic-move fallback and preservation of the prior archive after installation failure.
- Verify v2 schema, recipe identity, entry integrity and remote freshness survive save and reload.
- Verify corruption quarantine, quarantine failure and nonfatal rebuild behavior.
- Verify legacy filename mapping, stale last-known-good use, unmatched reasons, unreadable entries and untouched legacy state.
- Verify inspect and verify make no writes and never invoke the remote adapter.
- Verify offline migrate fails if implementation attempts network access.
- Verify online-refresh migration reacquires current remote images explicitly.
- Verify legacy cleanup refuses to run before successful verification or without explicit confirmation and deletes only the exact legacy target after approval.
- Verify human and JSON reports carry the same findings and outcome meaning.
- Verify invalid arguments, verification findings and operational failures use distinct nonzero exit-status categories.
- Use existing Icon Cache persistence tests, bootstrap scheduling tests, Ship Filter view tests, Admiral workspace tests and visual-baseline harness as prior art, but replace tests coupled to retired storage and loader operations.
- Retain visual baselines for reusable Roster, One-Time Ship, Starship Trait, selection-dialog, Ship usage and Solution card presentations.
- Extend architecture tests to reject every retired declaration anywhere in production source while permitting replaceable private implementation names.
- Add an architecture assertion that the intended Ship Artwork interface is the only public seam from its module.
- Run focused Ship Artwork and tooling tests during iteration, then the relevant Swing integration and architecture tests.
- Completion requires `.\gradlew.bat clean build` on the installed JDK 25.
- Run offline inspect, migrate and verify checks against representative legacy fixtures.
- Perform and record a real manual online refresh separately from automated verification.
- Record before/after visual evidence if implementation changes visible pixels or timing in a way screenshots can capture. Stop and reopen the decision if layout, selection, filtering or card structure changes.

## Out of Scope

- Replacing Swing or creating a non-Swing Ship Artwork interface.
- Exposing explicit listener registration, portfolio snapshots, demand generations or public batch-refresh operations to UI callers.
- Allowing callers to construct cache keys, select archive formats, provide executors or choose remote transports.
- Supporting configurable remote origins or arbitrary artwork providers.
- Downloading required faction backgrounds, role frames or rarity frames from the network.
- Applying refreshed GameData to the running application or recomposing artwork after a live GameData reload.
- Changing GameData Refresh, its transaction, its source adapters or its restart-to-apply contract.
- Changing Ship, Roster, One-Time Ship, Starship Trait, Assignment, Solution, deployment or scoring semantics.
- Changing Admirals XML, CSV formats, digest manifests or saved-Admiral compatibility.
- Automatically deleting legacy `icons.zip`.
- Migrating unknown legacy entries into the primary v2 archive without a canonical Ship mapping.
- Adding a general-purpose download manager, filesystem abstraction or process-wide event bus.
- Preserving source compatibility for callers of the retired factories, Icon Cache or loader.
- Creating implementation tickets as part of this specification publication.

## Further Notes

- The accepted highest application test seam is the three-operation Ship Artwork interface. The accepted operator seam is separate only because it is a headless command, but it uses the same implementation rather than creating duplicate migration behavior.
- Current GameData contains 558 Ships and 558 unique legacy icon filenames, so current entries can be mapped without filename collisions.
- The repository contains 206 bundled PNG resources, including 139 names matching current Ship image keys. No distributable `icons.zip` is stored in the repository; user archives are generated locally.
- Current Ship source images are typically 132 by 132 pixels and under 14 KiB, so the accepted network and decode limits leave substantial headroom.
- The deletion test is decisive: removing Ship Artwork after migration would spread composition, fallback, acquisition, repaint, freshness, retry, persistence and migration back across many callers.
- The stable live image handle is intentionally Swing-specific. ADR-0003 records why this deeper interface was chosen over explicit listeners, portfolios and public batch operations.
- The domain glossary now distinguishes Ship Artwork from the Icon Cache. The Icon Cache remains an implementation detail even though its persisted state has a documented migration format.
- The work should be delivered through green stages, but the final state is one complete migration with no compatibility interfaces.
- The specification is fully decided and labeled `ready-for-agent`; implementation may proceed without another product interview unless repository evidence contradicts a stated contract.

