# Ship Artwork maintenance

Run the Java 25 headless tool through Gradle, naming the exact directory that
contains the target archives and, for legacy inspection or migration, the five
GameData CSV files:

```powershell
.\gradlew.bat shipArtworkTool --args='inspect --data-directory C:\path\to\data'
.\gradlew.bat shipArtworkTool --args='migrate --data-directory C:\path\to\data --json'
.\gradlew.bat shipArtworkTool --args='migrate --data-directory C:\path\to\data --online-refresh'
.\gradlew.bat shipArtworkTool --args='verify --data-directory C:\path\to\data'
.\gradlew.bat shipArtworkTool --args='cleanup --data-directory C:\path\to\data --confirm-legacy-cleanup'
```

`inspect` inventories `icons.zip` and `ship-artwork-v2.zip` without changing
either archive. `migrate` admits recognizable legacy pixels as stale artwork in
the separate v2 archive and leaves `icons.zip` untouched. It is offline by
default and fails if any code attempts remote acquisition. `--online-refresh`
is valid only with `migrate`: it forces GitHub acquisition for canonical Ship
images that are not bundled, waits for all requested images to finish, and
reports requested, succeeded and failed source counts. Validation and composition
use the same Ship Artwork path as the application. A failed source is a finding;
existing valid artwork remains available.

`verify` reads and validates every v2 archive entry, including the manifest,
recipe, identities, digests, PNGs and source metadata. `cleanup` requires both a
successfully verified v2 archive in that invocation and
`--confirm-legacy-cleanup`. It deletes only the direct, regular `icons.zip` in
the resolved data directory. Missing, invalid or linked archive paths prevent
deletion. The cleanup status and target appear in both human and JSON reports.
`--json` is available for every operation.

For automation that needs the exact process exit code, assemble and call the
direct launcher with an installed JDK 25:

```powershell
.\gradlew.bat shipArtworkToolDistribution
& .\build\ship-artwork-tool\bin\ship-artwork-tool.bat verify --data-directory C:\path\to\data --json
$LASTEXITCODE
```

The Java process and direct launcher return 0 for a clean result, 2 for invalid
arguments, 3 for findings such as unmatched legacy entries or a refused cleanup,
and 4 for operational failures. Gradle reports the Java process exit value when
a nonzero status fails `shipArtworkTool`, while the Gradle wrapper returns its
own build-failure status. The JSON report includes `exitCode` in either launch
mode. Automated tests use scripted responses; they are not evidence of a live
GitHub refresh.
