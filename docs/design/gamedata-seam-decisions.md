# GameData seam — design decisions

Outcome of the architecture review (2026-08-26) and grilling session for candidate #1:
*make the Ship Database a deep module the Admiral accepts, not one it reaches into.*
This is the input for the implementation spec; implementation happens in a separate session.

Vocabulary: architecture terms per `/codebase-design` (module, interface, seam, adapter,
depth, locality, leverage); domain terms per `CONTEXT.md`.

## Problem being solved

- `beans.Admiral` calls `io.Datastore` statically; `Datastore` calls `ui.workers.SwingWorkerExecutor`,
  which loads `ui.resources.Images` and the AWT toolkit. Loading an `Admiral` therefore boots JAXB,
  reads `admirals.xml` from CWD and may start network downloads. Nothing in `beans` is testable.
- `Datastore.getAdmirals()` is a getter that mutates rosters (`validateShips`), fires the data-file
  updater and enqueues icon downloads.
- `Datastore` is the most-edited source file; the data-file updater that just landed writes CSVs the
  in-memory maps never reload.

## Decisions (Q# refers to the grilling rounds)

### Shape of the deep module
- **Q1** One `GameData` module owns all five CSVs (ships, renamed, traits, events, assignments).
  Interface: `ship(name)`, `ships()`, `events()`, `assignments()` (+ traits as needed by ships).
- **Q9** `GameData` is a concrete class, not a Java interface: `GameData.load(Path)` for production,
  `GameData.builder()...build()` for tests. No `InMemoryGameData` adapter — nothing varies in behaviour
  across the seam, only in how data gets in. Revisit if a second real source ever appears.
- **Q10** `ship(name)` is deep: folds case *and* follows `renamed.csv` internally, returning the
  current `Ship` for an old name. The renamed map is not part of the interface.
  `Admiral.validateShips` collapses to: for each saved name -> `gameData.ship(name)`; null -> drop;
  else store `ship.getName()`. Rename matching becomes case-insensitive (a fix, not a regression).
- **Q11** Fail fast: `GameData.load` and the Admirals loader throw a checked exception on any
  missing/unreadable file or JAXB failure. `AppBootstrap` catches once and shows `ExceptionDialog`.
  No silent empty maps.
- **Q3** `GameData` still hands out the same mutable `ShipImpl` objects; `setOwned` / `setUsageCount`
  mutation from `Admiral` is left as is. Moving per-player state off `Ship` belongs to candidate #2
  (Roster). **Recorded as follow-up debt.**

### How the Admiral gets it
- **Q2** `admiral.attach(GameData)` called after JAXB unmarshal; stored in an `@XmlTransient` field.
  Any lookup before attach throws `IllegalStateException` (loud invariant). Constructor injection
  waits for the DTO/domain split in candidate #2.
- **Q20** `Admirals` (the container) holds the `GameData` reference: `Admirals.attach(gameData)`
  forwards to every Admiral, and `addAdmiral` attaches newcomers. The UI never attaches.

### How the UI gets it (transitional)
- **Q4 / Q13** Root-package `App` holder: `App.gameData()`, `App.admirals()`, `App.dataDir()`,
  set once by `AppBootstrap`, throwing if read before bootstrap. `GameData`/`Admirals` themselves
  stay free of statics. UI constructor threading is deferred to candidate #5 (panel shrink).
  The cycle that matters is `beans -> io -> ui`; `ui -> io` is the right direction.

### Startup
- **Q8** `com.kor.admiralty.AppBootstrap` (root package, above `io` and `ui`), called from
  `AdmiraltyConsole.main` before any frame exists. Sequence:
  1. resolve data directory (Q17)
  2. `GameData.load(dataDir)`
  3. `AdmiralsStore.loadOrCreate(dataDir)` -> `admirals.attach(gameData)` -> validate each Admiral
  4. if data files stale -> schedule `UpdateDataFiles`
  5. if icon cache stale -> schedule icon prefetch for owned ships
  `AdmiraltyConsole.CONSOLE` / `STATS_FRAME` static initialisers become lazy.
- **Q5** Data-file update stays download-only; new CSVs take effect next launch. Make it explicit:
  bootstrap schedules it, completion logs/notifies "restart to apply". Live reload is a feature,
  not part of this refactor.
- **Q17** Data directory = directory of the running executable (jar/EXE via
  `getProtectionDomain().getCodeSource().getLocation()`) **if it contains `ships.csv`**, else CWD.
  See `docs/adr/0001-data-dir-beside-executable.md`.
- **Q14** `Datastore.file()` (the `FILES` cache) and `Datastore.copy()` are deleted; everything
  receives a `Path dataDir`. `FileDownloader` uses `Files.copy`. Configurability is deferred.

### Persistence of Admirals
- **Q12** One `io.AdmiralsStore`: `loadOrCreate(Path)`, `save(Path, Admirals)`,
  `exportShipNames(File, Collection<Ship>)`, `importShipNames(File, GameData, Admiral)`.
  JAXB context is built in its constructor (no static block, no swallowed `Throwable`).
  `importShipNames` stores `ship.getName()` (canonical), not the raw line.

### Icon cache (minimal move only; full consolidation is candidate #4)
- **Q6** `ui.resources.IconCache` owns the `ImageIcon` map, zip load/save (absorbs `io.IconLoader`),
  the changed flag and its own staleness check. `io` no longer imports Swing.

### Globals
- **Q18** `STYLESHEET_TRAIT` + CSS move to `ui.renderers.StarshipTraitCellRenderer` (only consumer).
  `isTimestampFresh/Stale` + `UPDATE_INTERVAL` move to the modules that decide freshness
  (`UpdateDataFiles`, `IconCache`). Filename constants, `URL_UPDATE`, `MAX_ASSIGNMENTS`,
  `SOLVER_DEPTH`, `DEBUG` stay. `Globals` becomes Swing-free.

### Build and tests
- **Q7** Historical baseline: the pom targeted Java 11 and added `javax.xml.bind:jaxb-api:2.3.1`
  plus `org.glassfish.jaxb:jaxb-runtime:2.3.x` to keep the `javax` namespace and the
  `admirals.xml` wire format unchanged. The current build targets Java 25 and uses the relocated
  `jakarta.xml.bind:jakarta.xml.bind-api:2.3.3` coordinate, whose API remains in the `javax`
  namespace, with `org.glassfish.jaxb:jaxb-runtime:2.3.9`. JUnit 5 and the `test/` source directory
  remain in place. Build via `mvn` from PowerShell — the Git Bash `mvn` script is broken on this
  machine.
- **Q15** Fixtures: small CSVs under `test/resources/gamedata/` (5-6 ships, one renamed entry,
  one trait) plus one smoke test loading the real `data/`. Scenarios:
  1. `ship("u.s.s. enterprise")` case-folds
  2. `ship(oldName)` follows renamed
  3. unknown name -> null
  4. missing `ships.csv` -> exception
  5. `Admiral.validateShips` drops unknown, migrates renamed, marks owned
  6. `getActiveShips` returns Ships in sorted order
  7. any lookup before `attach` throws
  8. `AdmiralsStore` round-trips `Admirals` through XML in a temp dir
- **Q19** Done means: (1) `mvn test` green on JDK 26 with `-Djava.awt.headless=true`;
  (2) app launches and shows admirals, ships and icons; (3) a source-scanning test fails if any file
  under `beans/` or `io/` imports `com.kor.admiralty.ui` — the guard that keeps the cycle broken.
- **Q22** (added at spec time) `AppBootstrap` is tested too. It accepts the candidate executable
  directory, the CWD, and a **background-jobs port** (`scheduleDataFileUpdate(dataDir)`,
  `scheduleIconDownload(ship)`) — Swing-worker adapter in production, recording fake in tests.
  Two adapters, so this is a real seam. Bootstrap throws a checked exception on load failure;
  showing `ExceptionDialog` is `main`'s job, not bootstrap's. Scenarios:
  9. exe dir containing `ships.csv` wins over CWD; exe dir without it falls back to CWD
  10. admirals are attached before validation (unknown ships are dropped, no use-before-attach error)
  11. stale `hashes.md5` -> data-file update scheduled exactly once; fresh -> not scheduled
  12. stale icon cache -> icon download scheduled for owned ships only; fresh -> none
  13. missing `ships.csv` -> bootstrap exception, no jobs scheduled
  Staleness stays mtime-based; tests set mtimes on temp fixtures instead of injecting a clock.

### Housekeeping (confirmed deletions — commented-out *code*, not explanatory comments)
- **Q16** `Datastore` deleted entirely, including its `/*/ ... /*/` toggle blocks (`downloadShipList`
  never existed; `getCacheTime`). `Solver.main` deleted (last `beans -> io` edge; could become a test
  in candidate #6). Six commented-out `download*` stubs in `SwingWorkerExecutor` deleted
  (superseded by `downloadFile`).

### Delivery
- **Q21** Implementation in a separate session via `/to-spec` and `/to-tickets`. Suggested
  step order for tickets: (1) pom + compile; (2) `GameData` + builder + tests; (3) `AdmiralsStore` + tests;
  (4) `IconCache` extraction; (5) `App` + `AppBootstrap` + rewire 27 call sites + delete `Datastore`;
  (6) `Globals` split + housekeeping + import-scan test; (7) launch and verify.

## Follow-ups deliberately left out of this change
- Per-player state (`owned`, `usageCount`) living on shared `Ship` objects -> candidate #2.
- Live reload of GameData after a data-file download -> feature, not refactor.
- Threading `GameData` through UI constructors instead of the `App` holder -> candidate #5.
- Full icon pipeline consolidation -> candidate #4.
- Configurable data directory -> one-line change in `AppBootstrap` when needed.

## Reference
- Spec (ready-for-agent): https://github.com/evildarkarchon/sto-aso/issues/1
- Architecture review report (temp file, regenerate with `/improve-codebase-architecture`):
  candidates #1-#6 with before/after diagrams.
- `Datastore` static call sites at time of review: 27 (beans: `Admiral` x4, `Solver.main` x1;
  io: `ShipDatabaseParser` x1; the rest in `ui/`).
