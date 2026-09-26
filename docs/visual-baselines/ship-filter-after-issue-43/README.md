# GameData Starship Trait Ship Filter comparison

These captures verify the Trait Viewer migration in issue #43. The existing
`game-data-traits` mode of `ShipFilterVisualBaseline` constructs the real
`TraitViewer` with published fixture GameData and the production artwork factory.
The window title retains the harness's established `Ship Filter Before` text
for exact comparison.

| Original baseline | Fresh pre-migration capture | Migrated capture |
| --- | --- | --- |
| [Baseline](../ship-filter-before/game-data-traits.png) | [Before](game-data-traits-before.png) | [After](game-data-traits.png) |

On Java 25 at Windows desktop scale 100%, the migrated image is 640 by 480
pixels and matches both the original baseline and a fresh capture from commit
`2e944e47524767db67e54669ff529a8b94cd9496` with **zero differing ARGB pixels**.
Visual inspection confirms the same Ship artwork, name, Starship Trait text,
column layout, margins, scrolling container, and window geometry.

Use the committed Gradle Wrapper with Java 25 to capture the current production
viewer without overwriting these reference images:

```powershell
.\gradlew.bat shipFilterVisualBaseline `
  --args="game-data-traits build/ship-filter-after-game-data-traits.png"
```
