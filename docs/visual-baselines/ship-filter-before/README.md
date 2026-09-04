# Ship Filter before-migration visual baseline

These screenshots record the established Swing presentation before the Ship
Filter migration tracked by GitHub issue #37. They were captured for issue #38
from the small `test/resources/gamedata` fixture with the deterministic
`ShipFilterVisualBaseline` tool on Java 25 at Windows desktop scale 100%. The
tool renders each real visible Swing window through `printAll`, which keeps the
capture reproducible when GUI automation and the launched JVM use separate
Windows stations.

| View | Baseline |
| --- | --- |
| Reusable Ship selection | [active-dialog.png](active-dialog.png) |
| One-Time Ship selection | [one-time-dialog.png](one-time-dialog.png) |
| Roster-card selection | [roster-card-dialog.png](roster-card-dialog.png) |
| Active and Maintenance Roster | [primary-roster.png](primary-roster.png) |
| One-Time Roster | [one-time-roster.png](one-time-roster.png) |
| Roster Starship Traits | [roster-traits.png](roster-traits.png) |
| GameData Starship Traits | [game-data-traits.png](game-data-traits.png) |
| Ship usage (initial Most Used order) | [ship-usage.png](ship-usage.png) |

The three dialog captures intentionally expand the filter pane. The two Ship
dialogs select the first visible Ship so the details column is populated; the
Roster-card dialog demonstrates the compact list-only layout.

## Reproducing the views

Compile test utilities and assemble their dependency classpath:

```powershell
mvn -DskipTests test-compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/visual-classpath.txt"
```

Then launch one view interactively, or provide a PNG path to capture it:

```powershell
$classpath = "target/test-classes;target/classes;$(Get-Content -Raw target/visual-classpath.txt)"
java -cp $classpath com.kor.admiralty.ui.ShipFilterVisualBaseline active-dialog
java -cp $classpath com.kor.admiralty.ui.ShipFilterVisualBaseline active-dialog `
  docs/visual-baselines/ship-filter-before/active-dialog.png
```

Valid view names are `active-dialog`, `one-time-dialog`,
`roster-card-dialog`, `primary-roster`, `one-time-roster`, `roster-traits`,
`game-data-traits`, and `ship-usage`.
