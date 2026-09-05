# Final Ship Filter visual and interaction verification

Issue #45 retires the legacy presentation stack. These final captures use only
the named `ShipFilterViews` paths and the migrated production consumers, with the
unchanged deterministic `test/resources/gamedata` fixture. No personal Admiral
state is read or written; the harness initializes its application dependencies
under `target/visual-baseline-data`.

All eight final captures were inspected on September 4, 2026 and compared against
the original issue #38 PNGs. **Every image has identical dimensions and zero
differing ARGB pixels.** The original screenshots remain unchanged.

| Presentation | Original | Final | Dimensions |
| --- | --- | --- | --- |
| Reusable Ship dialog | [Before](../ship-filter-before/active-dialog.png) | [After](active-dialog.png) | 683 × 1121 |
| One-Time Ship dialog | [Before](../ship-filter-before/one-time-dialog.png) | [After](one-time-dialog.png) | 683 × 1121 |
| Roster-card dialog | [Before](../ship-filter-before/roster-card-dialog.png) | [After](roster-card-dialog.png) | 423 × 1121 |
| Active and Maintenance lists together | [Before](../ship-filter-before/primary-roster.png) | [After](primary-roster.png) | 1100 × 760 |
| One-Time list | [Before](../ship-filter-before/one-time-roster.png) | [After](one-time-roster.png) | 1100 × 760 |
| Roster Starship Traits | [Before](../ship-filter-before/roster-traits.png) | [After](roster-traits.png) | 1100 × 760 |
| GameData Starship Traits | [Before](../ship-filter-before/game-data-traits.png) | [After](game-data-traits.png) | 640 × 480 |
| Ship usage | [Before](../ship-filter-before/ship-usage.png) | [After](ship-usage.png) | 1024 × 1406 |

Inspection confirms the established labels, margins, card artwork, selection
highlight, Ship details, Active/Maintenance counts, duplicate One-Time cards and
their quantities, trait text, and usage counts. The fixture's usage sequence is
12, 7, 7, 4, 2 in the initial Most Used presentation.

## Reproduce the captures

Use Java 25 and the same Windows desktop scale (100%). Each screenshot renders
a real visible Swing window with `printAll`, matching the original baseline
method. The historical `Ship Filter Before` frame titles remain deliberately
unchanged so the complete window image is comparable; all content is current.
Temporary `-after` view names have been removed with the legacy implementations.

```powershell
mvn -DskipTests test-compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/visual-classpath.txt"
$classpath = "target/test-classes;target/classes;$(Get-Content -Raw target/visual-classpath.txt)"
$views = @('active-dialog', 'one-time-dialog', 'roster-card-dialog',
  'primary-roster', 'one-time-roster', 'roster-traits', 'game-data-traits', 'ship-usage')
foreach ($view in $views) {
  & "$env:JAVA_HOME/bin/java.exe" -cp $classpath `
    com.kor.admiralty.ui.ShipFilterVisualBaseline $view "target/final-$view.png"
}
```

Omit the output path to leave a view open for interactive inspection. Capture
into `target/` when verifying locally so checked-in references are preserved.

## Native interaction evidence

The following native Swing run completed with exit code 0:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -cp $classpath `
  com.kor.admiralty.ui.ShipFilterVisualBaseline interaction-smoke
```

The harness drives actual displayed controls on the event-dispatch thread. It
opens the production modal factory methods, allowing their normal modal event
loops to process the timer-driven actions, and asserts the resulting selection.
It verifies:

- Reusable, One-Time, and Roster-card dialogs each accept, cancel, and close from
  the window control: nine native modal outcomes. Acceptance returns the exact
  selected objects in visible order; cancellation and close return empty lists.
- Every one of the 21 filter checkboxes is toggled twice in every dialog case
  and the native usage frame. Each round trip restores the exact row identities
  and order. Clearing all dimensions produces no rows, then restoration restores
  the original row count.
- Usage begins with the 12-deployment Ship. All three ordering controls preserve
  the five fixture rows; Most Used and Least Used produce monotonic deployment
  counts. Every available Admiral group/individual option can be selected; the
  individual Admiral produces the five fixture rows. Closing hides the native
  frame and reopening displays it again.
- A production Admiral workspace dispatches primary-button double-click events
  inside an Active cell and its resulting Maintenance cell. The exact card ID
  moves to Maintenance and back to Active, retaining the canonical Ship; list
  counts change from 2/2 to 1/3 and back to 2/2.
- Constraining the real workspace frame creates scroll overflow in the Active,
  Maintenance, One-Time, and Roster Starship Trait presentations. Dispatched
  wheel events move all three Roster list viewports while retaining exact model
  entries and order. The One-Time list retains three independent copies,
  including the two distinct Enterprise card IDs.
- Both Roster and standalone GameData Starship Trait presentations contain only
  the fixture's trait-bearing Enterprise. The standalone viewer also receives
  wheel input at a constrained native size; resizing both windows retains the
  trait content. With this single-row fixture, neither trait list moves on unit
  wheel input; operating its scrollbar does move the viewport and retains its
  exact content. The harness records that behavior rather than assuming a wheel
  event must move a list with no next row.

The passive window checks can also be run independently with the `passive-smoke`
mode. They remain included in the complete `interaction-smoke` run.

These are **programmatic native-window checks and image inspection**, not a
claim of human manual testing or physical mouse/keyboard automation. Dispatched
mouse and wheel events exercise Swing handling, but physical focus navigation,
hardware wheel input, and an end-to-end human Roster workflow remain unverified
by this harness. The regression suite separately covers semantic activation,
Roster transitions, scrolling policies, trait filtering, refresh/publication,
and selection identity contracts.
