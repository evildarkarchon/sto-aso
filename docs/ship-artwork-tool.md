# Offline Ship Artwork maintenance

Run the Java 25 headless tool through Gradle, naming the exact directory that
contains the target archives and, for legacy inspection or migration, the five
GameData CSV files:

```powershell
.\gradlew.bat shipArtworkTool --args='inspect --data-directory C:\path\to\data'
.\gradlew.bat shipArtworkTool --args='migrate --data-directory C:\path\to\data --json'
.\gradlew.bat shipArtworkTool --args='verify --data-directory C:\path\to\data'
```

`inspect` inventories `icons.zip` and `ship-artwork-v2.zip` without changing
either archive. `migrate` admits recognizable legacy pixels as stale artwork in
the separate v2 archive. It leaves `icons.zip` untouched and refuses any remote
acquisition. `verify` reads and validates every v2 archive entry, including the
manifest, recipe, identities, digests, PNGs and source metadata. A missing or
invalid v2 archive is a finding. `--json` projects the same report fields used
for human output.

For automation that needs the exact process exit code, assemble and call the
direct launcher with an installed JDK 25:

```powershell
.\gradlew.bat shipArtworkToolDistribution
& .\build\ship-artwork-tool\bin\ship-artwork-tool.bat verify --data-directory C:\path\to\data --json
$LASTEXITCODE
```

The Java process and direct launcher return 0 for a clean result, 2 for invalid
arguments, 3 for findings such as unmatched legacy entries, and 4 for operational
failures. Gradle reports the Java process exit value when a nonzero status fails
`shipArtworkTool`, while the Gradle wrapper returns its own build-failure status.
The JSON report includes `exitCode` in either launch mode.
