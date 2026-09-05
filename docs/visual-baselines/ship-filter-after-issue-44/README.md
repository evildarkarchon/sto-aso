# Ship usage Ship Filter comparison

These captures verify the Ship usage migration in issue #44. The existing
`ship-usage` mode of `ShipFilterVisualBaseline` constructs the real usage window
with isolated Admiral history and the production artwork factory. Its established
window title is retained for exact comparison.

| Original baseline | Fresh pre-migration capture | Migrated capture |
| --- | --- | --- |
| [Baseline](../ship-filter-before/ship-usage.png) | [Before](ship-usage-before.png) | [After](ship-usage.png) |

On Java 25 at Windows desktop scale 100%, both fresh images are 1024 by 1406
pixels and match with **zero differing ARGB pixels**. The pre-migration capture
was produced from commit `8821727c4fa21c96c7e88eab655e1e668bb77cdf`.
The original issue #38 baseline differs only in the native Java window icon
(256 pixels within x=10–25, y=11–26); the application content matches exactly.
Visual inspection confirms the same list-only layout, controls, Ship artwork,
usage counts, Most Used ordering, margins, and scrolling container.

Compile the test utilities and assemble their dependency classpath as described
in the [baseline instructions](../ship-filter-before/README.md), then capture
the current production usage window without overwriting these references:

```powershell
$classpath = "target/test-classes;target/classes;$(Get-Content -Raw target/visual-classpath.txt)"
java -cp $classpath com.kor.admiralty.ui.ShipFilterVisualBaseline ship-usage `
  target/ship-filter-after-ship-usage.png
```

Consumer tests exercise the real usage content without a native frame: every
Admiral group and individual choice, all three sort controls, filter retention,
history refresh, and one final list-data event per complete operation. Named-view
tests separately cover canonical tie-breaking, each filter dimension, immutable
original row observations, rendering, and entry/ordering type safety.
