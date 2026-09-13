Status: resolved
Blocked by: 01

# 04: Make Ship Filter visual verification a Gradle task

**What to build:** Let UI maintainers run every Ship Filter visual-baseline mode through one non-headless Gradle task instead of manually assembling Java and dependency classpaths.

- [x] The task uses Java 25, the complete test runtime classpath, the project working directory, and standard Gradle argument forwarding.
- [x] The task invokes the harness's existing flexible main method without changing its visibility or production Java behavior.
- [x] Automated tests remain headless while the visual task explicitly runs non-headless and can display real Swing windows.
- [x] Harness-owned scratch state is written beneath the Gradle build directory, while caller-selected image destinations remain controlled by task arguments.
- [x] Current visual-baseline reproduction and interaction instructions use the dedicated Gradle task and no longer construct a classpath manually.
- [x] At least one image-producing mode completes in a non-headless Windows session and its result is checked against the corresponding baseline under the documented display conditions.
- [x] The interaction-smoke mode exits successfully through the same task.
- [x] Running the active visual workflow does not recreate Maven-era output directories.

## Comments

Implemented `shipFilterVisualBaseline` as a Java 25, non-headless `JavaExec` task using the complete test runtime classpath, project working directory, and Gradle's standard `--args` forwarding. The package-private flexible `main` remains unchanged. Harness scratch state now resolves from Gradle's `build/visual-baseline-data`, independently of caller-selected image destinations.

Verified on September 12, 2026 in a non-headless Windows Java 25 session. The `game-data-traits` mode produced a PNG whose SHA-256 exactly matched `docs/visual-baselines/ship-filter-after-issue-45/game-data-traits.png`, and `interaction-smoke` completed every dialog, usage, Roster, and GameData Traits case with exit code 0. A before/after snapshot confirmed neither workflow changed the pre-existing Maven `target/` tree. `BuildHarnessTest` separately remained green with headless AWT enabled.

Gradle's local state and build output were already tracked from the earlier migration work. At maintainer direction, `.gradle/` and `build/` are now ignored and their generated contents are removed from Git tracking; the separate Maven `target/` retirement remains deferred to ticket 07.
