## Parent

Part of #46 — Finish the Assignment editor migration through the immutable view seam.

## What to build

Make the Assignment editor's declared mutation interface consistently reject calls outside Swing's event-dispatch thread before changing editor state, while preserving normal Assignment editing and Ship/Solution presentation. This ticket is independently implementable against the existing immutable-view interface and does not require legacy-binding retirement first.

## Acceptance criteria

- [ ] Add an explicit event-dispatch-thread guard at entry to the surviving GameData/artwork constructor. Preserve dependency null validation; a late failure from a presentation helper is not sufficient construction guarding.
- [ ] Add entry guards to all five retained public presentation methods: setShip1, setShip2, setShip3, clearSolutions, and clearShips. Off-thread calls throw IllegalStateException before any editor state is changed, including retained Solution state and displayed Ship cards.
- [ ] Preserve the existing guards on setAssignmentView, setAssignmentSolution, and clearAssignment, including bound-view preconditions. Do not retire or privatize these operations or the five additional guarded methods.
- [ ] Add focused headless JUnit 5 coverage for construction and every declared mutation operation. Create test editors on the event thread, exercise mutations off-thread, and demonstrate failure without partial changes to bound view/callback owner, retained Solution, or displayed Ship state; include all five added guards and normal event-thread success behavior using existing GameData fixtures and artwork adapters.
- [ ] Preserve workspace editing and disposal on the event thread. Do not extend the contract to inherited Swing setters such as setVisible, add indiscriminate guards to Swing callbacks, or change existing focus-event dispatch.
- [ ] Document threading and failure semantics with concise Javadoc on affected operations and the constructor. Preserve accurate comments and disclose any comment rewrites in the implementation summary.
- [ ] Run the focused editor tests and relevant workspace tests, then obtain a green `mvn clean test` on Java 25. Run `graphify update .` after code changes and record verification. Preserve player-visible behavior and existing dependency/rendering interfaces, numeric validation, domain rules, and formats.
- [ ] Perform an actual focused Swing walkthrough of Assignment/Event selection, manual entry, Solution display, and workspace closure, recording revision, actions, outcomes, and gaps separately from automated control interactions or rendering checks. Do not report an outstanding walkthrough as completed. Include before/after screenshots if a separately agreed visible Swing change occurs.
- [ ] If this is the last implementation ticket to land, run focused editor and architecture tests, retained workspace tests, `mvn clean test`, and the actual walkthrough against the combined revision including the sibling binding contraction. Retained workspace coverage must include Assignment/Event and critical-chance editing, counts, correct Admiral/slot ownership, successive authoritative edits, Solution values/navigation/invalidation, exact RosterCard deployment, and disposal. Add meaningful coverage only where missing. Otherwise record this ticket's revision and leave combined acceptance to the last landing ticket. Do not claim #46 complete until both changes and required verification are complete.

Coordinate edits to the shared editor with the sibling contraction ticket as needed; shared files alone do not impose a functional blocking dependency. The last ticket to land owns combined acceptance. No new production test seam, dependency abstraction, format migration, inherited-Swing threading contract, or unrelated behavioral correction is in scope; newly discovered defects require a separate scope decision. Preserve existing ADR and glossary decisions.

## Blocked by

None (can start immediately).
