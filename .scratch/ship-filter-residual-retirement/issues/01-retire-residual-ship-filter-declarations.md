Status: resolved
Blocked by:

# 01: Retire the residual Ship Filter declarations and seal the supported seam

## What to build

Finish the residual Ship Filter retirement so the repository exposes only the supported Ship Filter module. Remove the unreachable table-based filtering stack and unused dialog-selection helper without replacing them with compatibility types, preserve every reachable Ship Filter and Swing behavior, durably prevent the retired declarations from returning, and record the cleanup without rewriting the historical migration evidence.

## Acceptance criteria

- [x] The existing retired-Ship-Filter architecture guard is broadened and accurately named to reject declarations of `ShipRowFilter`, `ShipTableModel`, `IntegerComparator`, and `DialogSelections` anywhere in production source, including package-private declarations in renamed or relocated files, while preserving the established Ship Selection string-resource exclusion.
- [x] The broadened architecture guard is observed failing while the four declarations still exist, then its focused test passes after retirement.
- [x] The four obsolete production declarations and the test dedicated only to `DialogSelections` are deleted; the resulting empty models package is absent; and no deprecated type, forwarding type, alias, adapter, facade, or alternate implementation replaces them.
- [x] Production code in the supported Ship Filter module is unchanged, and existing focused view tests pass for reusable Ship, One-Time Ship, and Roster-card acceptance, cancellation, window closure, empty acceptance, visible ordering, exact entry identity, and immutable results.
- [x] A clean full Gradle build passes on the installed JDK 25, confirming that deletion leaves no unresolved references, hidden build inputs, or architecture failures.
- [x] The Ship Filter design record receives a dated residual-retirement follow-up, and the verification record receives separate current evidence covering the four removed declarations, removal of the obsolete models package, retained live dialog coverage, and current verification commands without altering historical scope, commands, counts, screenshots, graph-refresh evidence, or manual-verification limitations.
- [x] The repository CodeGraph index is refreshed after deletion so architecture exploration no longer returns the retired symbols.
- [x] The implementation summary reports that comments and Javadocs disappeared only with the deleted production types and their deleted test, and confirms that no unrelated Ship Filter behavior, presentation, glossary, ADR, or domain area changed.

## Comments

Resolved on September 18, 2026. Deleted the four residual production
declarations and `DialogSelectionsTest`, removed the now-empty `ui.models`
package, broadened the existing declaration guard, and added separate dated
follow-ups to the Ship Filter design and verification records. The guard was
observed red before deletion and green afterward; `ShipFilterViewTest` and a
clean full Gradle build passed on Eclipse Temurin 25.0.4.1. CodeGraph is current
and returns none of the retired symbols.

Comments and Javadocs disappeared only with the deleted production types and
their deleted test. Production code in the supported Ship Filter module is
byte-for-byte unchanged, and no unrelated Ship Filter behavior, presentation,
glossary, ADR, or domain area changed.
