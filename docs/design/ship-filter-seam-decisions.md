# Ship Filter seam — design decisions

Outcome of the architecture review and grilling session for deepening Ship
filtering and selection. This document is input for a later implementation
change; no production behavior is implemented here.

Vocabulary: architecture terms per `/codebase-design` (module, interface,
implementation, depth, seam, adapter, leverage, locality); domain terms per
`CONTEXT.md`.

## Problem being solved

- `ui.ShipListPanel` and `ui.ShipSelectionPanel` are nearly 900 lines each and
  duplicate the same collapsed filter presentation, 21 checkbox actions,
  player-faction profiles, scrolling behavior and dialog selection mechanics.
- `ui.models.AbstractShipListModel` exposes the filtering implementation as 21
  mutable booleans. Callers and tests must understand the same faction, role,
  tier and rarity switches that the module should hide.
- `ShipSelectionPanel` additionally owns a `ShipDetailsPanel`, while
  `ShipListPanel` serves Roster-card selection and Ship usage. This legitimate
  presentation difference is mixed with otherwise duplicated implementation.
- The concrete list models adapt `Ship`, `RosterCard` and `ShipUsageRow`, but
  their shared filtering and event-publication behavior remains caller-visible.
- Focused coverage is limited to usage ordering and one faction exclusion.
  Neither real panel has direct behavioral tests for filter profiles, selection,
  dialog outcomes or Ship details.

The deletion test is decisive: removing either duplicated panel makes most of
its filter implementation disappear, while removing the boolean facade
concentrates the real behavior in one place. The current modules are shallow.

## Domain meaning

**Ship Filter** is the visibility and ordering criteria applied when presenting
Ships from GameData, a Roster or usage history. It uses canonical Ship facts and
never changes GameData or a Roster. `CONTEXT.md` records this term and rejects
"Ship Selection" and "Ship Browser" as narrower presentation names.

The Ship Filter applies to three current entry kinds:

- `Ship` from GameData;
- identity-bearing `RosterCard` values from a Roster;
- immutable `ShipUsageRow` values from usage history.

## Change envelope

- This is one complete migration. Every production reference moves before the
  old modules are deleted; no compatibility interface remains.
- Current layouts, control labels, filter defaults, ordering choices, multiple
  selection, optional Ship details and dialog outcomes remain functionally
  equivalent.
- Applying a complete Ship Filter publishes one final projection and one Swing
  list-data event. The current sequence of intermediate sorts and events is an
  implementation detail and is not preserved.
- Entry replacement preserves selected entry identities that remain visible and
  clears identities that disappear. Raw index retention is intentionally not
  preserved because it can silently select a different RosterCard.
- Java 25 best practices replace fragile implementation without changing the
  remaining functionality: immutable value carriers, final implementation types,
  defensive copies, explicit stable ordering, typed values and explicit Swing
  event-thread confinement.

## Deep module shape

The module lives under `com.kor.admiralty.ui.shipfilter`. It contains:

1. a headless, thread-agnostic Ship Filter core;
2. one reusable Swing presentation;
3. package-private entry adapters and projection implementation;
4. module-owned presets for every established production presentation.

This package placement preserves the existing dependency direction. No `beans`
or `io` module needs Ship Filter behavior.

### Headless interface

The external headless seam is a typed immutable `ShipFilter<E, O>`. The entry
type and ordering type are fixed together so unsupported combinations cannot be
represented.

```java
public final class ShipFilter<E, O> {

    public List<E> project(Collection<? extends E> entries);

    public ShipFilter<E, O> allowingFactions(Set<ShipFaction> factions);

    public ShipFilter<E, O> allowingRoles(Set<Role> roles);

    public ShipFilter<E, O> allowingTiers(Set<Tier> tiers);

    public ShipFilter<E, O> allowingRarities(Set<Rarity> rarities);

    public ShipFilter<E, O> withOrder(O order);
}
```

Callers obtain correctly typed filters from module-owned factories:

```java
ShipFilters.ships();
ShipFilters.shipsForAdmiral(faction);
ShipFilters.oneTimeShipsForAdmiral(faction);
ShipFilters.rosterCards();
ShipFilters.usageRows();
```

The factories select the entry adapter, supported ordering type, default order
and initial immutable criteria. Callers never provide a Ship extractor,
comparator or adapter.

### Swing interface

`ShipFilterViews` binds the existing `ShipIconFactory` seam and exposes named
production paths. Named paths hide renderer choice, scrolling, column layout,
filter-control visibility, selection mode, optional Ship details and initial
Ship Filter.

The established paths are:

- reusable Roster presentation;
- One-Time Roster presentation;
- Roster Starship Trait presentation;
- GameData Starship Trait presentation;
- Ship usage presentation;
- reusable Ship selection;
- One-Time Ship selection;
- RosterCard selection.

Embedded views keep a small interface:

```java
view.present(entries);
view.orderBy(order);
view.onActivation(action);
```

`present` replaces entries atomically while retaining the current Ship Filter
and ordering. `orderBy` accepts only the ordering type paired with the view's
entry adapter. `onActivation` supplies the established double-click Roster
behavior without exposing the internal `JList`.

Selection dialogs return immutable selected-entry lists in visible order.
Checkbox state, selected indices, `ShipDetailsPanel` synchronization,
`JOptionPane` mechanics and list-model events remain inside the implementation.

`RosterSelectionDialog` remains the existing internal seam: the production
Swing adapter delegates to Ship Filter selection, while deterministic test
adapters continue to return selected Ships or Roster cards without opening a
window. Two adapters make this a real seam.

## Internal adapters

Three package-private adapters sit at one internal seam:

- the Ship adapter returns the supplied canonical Ship and applies
  `ShipSortOrder`;
- the RosterCard adapter returns `RosterCard.getShip()` and stably orders exact
  card identities by canonical Ship facts;
- the ShipUsageRow adapter returns `ShipUsageRow.ship()` and applies
  `ShipUsageSortOrder`.

These adapters represent proven variation. They remain internal because
exposing extraction, comparison or renderer selection would make the external
interface shallow. A fourth entry kind changes this module rather than
registering caller-owned behavior.

All projection dependencies are in-process, so no new port is justified.
`ShipIconFactory` remains a real Swing seam because production and test adapters
already exist.

## Filter semantics

- Faction, role, tier and rarity are ANDed across dimensions.
- Allowed values within one dimension are ORed.
- Every classified value is initially allowed.
- An empty allowed-value set hides every classified value in that dimension.
- Unclassified enum values retain the established pass-through behavior, but the
  implementation represents that behavior explicitly rather than relying on a
  switch default.
- Small Craft remains governed by the tier dimension rather than the role
  dimension.
- Inputs are copied. Returned projections are immutable and retain the exact
  original entry identities.
- Duplicate entries remain distinct unless an established selection path
  explicitly deduplicates them.

The immutable implementation uses defensive `Set.copyOf` and `List.copyOf` at
the interface. Internal enum calculations may use private `EnumSet` copies; no
mutable collection crosses the seam.

## Module-owned profiles

Admiral faction profiles remain functionally equivalent:

- Federation and Jem'Hadar-Federation Admirals see Federation, Jem'Hadar and
  Universal Ships.
- Klingon and Jem'Hadar-Klingon Admirals see Klingon, Jem'Hadar and Universal
  Ships.
- Romulan-Federation Admirals additionally see Romulan Ships with the Federation
  profile.
- Romulan-Klingon Admirals additionally see Romulan Ships with the Klingon
  profile.

The One-Time Ship selection profile applies the appropriate Admiral faction
profile and permits Tier 6 only. It explicitly deduplicates canonical candidate
Ship types before presentation, preserving the current selection functionality
without using incidental `TreeSet` side effects.

Profiles are complete immutable Ship Filters. Installing one profile produces
one projection and one event rather than a sequence of boolean mutations.

## Ordering

- Canonical Ship ordering remains tier, rarity, role and then case-sensitive
  canonical Ship name.
- RosterCard ordering uses canonical Ship ordering. Stable sorting preserves the
  input order of distinct cards whose canonical Ship facts compare equally.
- Ship usage supports the existing Default, Most Used and Least Used orders.
  Usage-count ties use canonical Ship ordering.
- The Ship usage presentation starts at Most Used before its first entries are
  published.
- Replacing entries retains the view's selected ordering.

Comparators state all tie-breakers explicitly. Sorting never mutates a
caller-owned collection.

## Selection and presentation

- Selection remains Swing multiple-interval selection.
- Accepted dialogs return selected entries in ascending visible-index order.
- Cancel, window close and acceptance with no selection all return `List.of()`.
- Replacing entries retains selected entry identities that remain visible,
  regardless of their new indices. Removed or newly hidden identities are
  cleared.
- Ship-selection dialogs retain the two-column layout and internal
  `ShipDetailsPanel` showing the selected Ship.
- RosterCard selection and Ship usage retain their list-only layout.
- Passive Active, Maintenance, One-Time and Starship Trait views retain their
  current renderers, scrolling and activation behavior.

## Threading and errors

- Headless projection is deterministic, thread-agnostic and side-effect free.
- Swing view construction and mutation require the event-dispatch thread and
  throw `IllegalStateException` when called elsewhere.
- A null dependency, filter value, collection, entry, enum value or activation
  action throws `NullPointerException` naming the invalid argument before any
  state is published.
- Unsupported entry/order combinations cannot be expressed through the typed
  factories.
- A failed validation leaves the prior Swing projection and selection intact.

## Retired modules

The types below have been removed in the issue #45 contraction. See the
[retirement verification report](ship-filter-retirement-verification.md) for
replacement coverage, compatibility effects, and remaining manual verification.

After every caller migrates, delete:

- `ui/ShipSelectionPanel.java`;
- `ui/ShipListPanel.java`;
- `ui/models/AbstractShipListModel.java`;
- `ui/models/ShipListModel.java`;
- `ui/models/RosterCardListModel.java`;
- `ui/models/ShipUsageListModel.java`.

Production consumers include more than the three filtered screens. The
migration therefore also covers Active, Maintenance, One-Time and Starship
Trait Roster lists, Trait Viewer and the list-model diagnostic. No deprecated
adapter or forwarding module retains the old interface.

## Test strategy

Characterization tests are written first, then replaced by tests at the new
interface. Old model tests do not remain layered beneath equivalent Ship Filter
tests.

### Headless interface tests

Cover:

1. all-visible defaults;
2. every faction, role, tier and rarity value;
3. AND across dimensions and OR within one dimension;
4. every Admiral faction profile;
5. the One-Time Ship Tier-6-only profile;
6. explicit unclassified-enum pass-through behavior;
7. Small Craft tier behavior;
8. canonical, Most Used and Least Used ordering;
9. stable equal-Ship RosterCard identity ordering;
10. One-Time Ship candidate deduplication;
11. retained duplicates on paths that do not deduplicate;
12. defensive copying and immutable projections;
13. retention of original entry identities;
14. null rejection before publication.

### Swing interface tests

Cover:

1. all 21 controls map to one immutable Ship Filter;
2. one user action publishes one projection and event;
3. complete profiles publish no intermediate state;
4. entry replacement retains filter and ordering state;
5. selection follows retained entry identities rather than indices;
6. hidden or removed identities lose selection;
7. multiple selection returns visible order;
8. cancel, close and empty acceptance return an empty immutable list;
9. Ship selection updates and clears Ship details;
10. non-Ship presentations omit Ship details;
11. activation reports the exact visible RosterCard;
12. off-event-thread construction and mutation fail loudly.

Focused consumer tests verify the three selection-dialog paths, passive Roster
and Starship Trait views, Trait Viewer and every Ship usage ordering. Existing
Admiral workspace integration tests continue to use the `RosterSelectionDialog`
test adapter.

`ArchitectureTest` gains durable assertions that the retired types cannot
return and Ship Filter implementation remains under `ui.shipfilter`. It does
not assert private class names or other replaceable implementation details.

## Delivery sequence

The work is one migration delivered through small green stages:

1. characterize current functional behavior;
2. add the headless Ship Filter and interface tests;
3. add the reusable Swing presentation and focused tests;
4. migrate all selection dialogs;
5. migrate passive Roster and Starship Trait lists;
6. migrate Trait Viewer and the diagnostic;
7. migrate Ship usage;
8. delete the old panels and models;
9. replace obsolete tests and add architecture assertions;
10. run full automated and manual verification.

## Definition of done

1. `mvn clean test` is green on the configured Java 25 build.
2. Headless Ship Filter tests cover every dimension, profile, adapter, ordering,
   identity, null and immutability contract.
3. Focused Swing tests cover controls, event coalescing, selection identity,
   dialogs and optional Ship details.
4. Manual launch verification covers all three selection dialogs, every passive
   list and Ship usage.
5. Before/after screenshots confirm that the visible Swing layout is unchanged.
6. The retired modules and their shallow interface are absent.

## Decisions deliberately outside this change

- No GameData, Roster, Solver, Special Ability or Icon Cache behavior changes.
- No new entry-kind registration seam; three internal adapters are sufficient.
- No Swing redesign, new filter dimension or new ordering choice.
- No change to modal dialog outcome semantics.
- No ADR is required. The module shape is reversible, follows existing package
  direction and does not reopen ADR-0001.
