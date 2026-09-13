# Ship Filter retirement verification

Issue [#45](https://github.com/evildarkarchon/sto-aso/issues/45) completes the
contraction stage of [#37](https://github.com/evildarkarchon/sto-aso/issues/37).
The production cutovers from issues #40–44 are retained unchanged.

## Retired interfaces

Deleted `ShipSelectionPanel`, `ShipListPanel`, `AbstractShipListModel`,
`ShipListModel`, `RosterCardListModel`, and `ShipUsageListModel`. No wrapper or
deprecated facade replaces them. `Strings.ShipSelectionPanel` remains solely as
the existing label namespace, not a presentation implementation.

The visual harness now uses only the current named paths. Historical PNGs are
unchanged; reproducing the historical implementations requires their historical
revision rather than a second supported implementation in the current tree.

## Behavior coverage before deleting characterization tests

| Deleted test | Retained public-interface coverage |
| --- | --- |
| `ShipListModelCharacterizationTest` | `ShipFilterTest`: all 21 singleton criteria, AND/OR composition, historical values, Small Craft, canonical ordering |
| `RosterCardListModelCharacterizationTest` | `ShipFilterTest` RosterCard projection and stable equal-Ship identity order; `ShipFilterViewTest` identity retention and clearing |
| `ShipUsageListModelTest` | `ShipFilterTest` supported usage orders and ties; `ShipUsageViewTest` typed ordering, filters, and publication |
| `ShipSelectionPanelCharacterizationTest` | `ShipFilterTest` all six Admiral profiles for both Ship factories; `ShipFilterViewTest` complete initial profiles, details, and modal outcomes |
| `ShipListPanelCharacterizationTest` | `ShipFilterViewTest` list-only RosterCard selection, multiple intervals, exact identities, and visible-order outcomes |

The singleton criteria matrix was transferred to the headless interface before
the old tests were deleted. The headless suite passed all 40 cases before
retirement. Existing consumer tests remain in place, including Admiral workspace,
usage panel, Trait Viewer, and diagnostic tests.

## Production paths and architecture

| Consumer | Named Ship Filter path |
| --- | --- |
| `RosterSelectionDialog.swing()` | `chooseReusableShips`, `chooseOneTimeShips`, `chooseRosterCards` |
| Active/Maintenance `ShipRosterPanel` | `reusableRoster` |
| `OneTimeShipPanel` | `oneTimeRoster` |
| `StarshipTraitsPanel` | `rosterStarshipTraits` |
| `TraitViewer` | `gameDataStarshipTraits` |
| `ShipUsagePanel` | `shipUsage` |
| `ShipFilterDiagnostic` | `ShipFilters.ships().project(...)` |

`ArchitectureTest` rejects the six retired declarations, limits public module
types to the four agreed entry points, and confines headless and Swing
implementation dependencies to `ui.shipfilter`. The guards follow helpers
without enumerating their private names. Domain values and the specific existing
artwork, resource, list-component, and Ship-details dependencies remain permitted.
Wildcard imports and fully qualified references participate in the source walk;
a synthetic source-tree test covers both without treating prose as dependencies.

## Verification evidence and limits

The retirement assertion was observed failing against the legacy panel before
deletion. Focused architecture and headless verification passed after the
changes. On September 4, 2026, `mvn clean test` passed on Eclipse Temurin
25.0.4.1 with `java.awt.headless=true`: **271 tests, zero failures, zero errors,
zero skipped**. The synthetic dependency-discovery test was also observed
failing when wildcard and fully qualified discovery were temporarily disabled;
the restored implementation passed in the clean suite.

The final standards review reported zero violations or actionable code smells.
The spec review's two architecture findings were fixed and re-reviewed, leaving
no implementation findings. The human manual verification limitation remains.
`graphify update .` refreshed the local code graph after retirement.

[Final screenshots and native interaction evidence](../visual-baselines/ship-filter-after-issue-45/README.md)
cover all affected presentations. All eight captures match the original issue
#38 baselines exactly, including dimensions and every ARGB pixel. That report
distinguishes programmatic native-window checks from the remaining human manual
walkthrough; issue #45 should not be treated as fully manually verified on the
strength of automated checks alone.

## Compatibility and scope

There are no data-format, CSV, digest-manifest, JAXB/XML, or saved-Admiral
compatibility changes. No domain, persistence, scoring, Icon Cache, or production
consumer behavior was changed. The architecture effect is removal of the old
public UI types and enforcement of the existing presentation-module boundary.
External source code using retired types must adopt the named Ship Filter paths;
the intentionally removed types have no compatibility facade.

Comments in deleted legacy files and deleted visual-harness helpers were removed
with their code. The harness class documentation and architecture test description
were revised because the old/current implementation split no longer exists.
