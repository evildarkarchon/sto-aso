---
status: accepted
---

# Use Gradle with Kotlin DSL as the sole build system

Replace Maven with Gradle as a personal project choice and use Kotlin DSL, which Gradle recommends for new builds, rather than Groovy DSL. This is not based on a claim that Gradle is intrinsically better than Maven. Keep the migration limited to the build system: preserve dependency versions, the existing source layout, Java and runtime behavior, the thin JAR with external GameData, and the documented OpenRewrite capability. Use Maven only for parity checks on the migration branch and remove the Maven build before merging so the repository has one authoritative build.

## Consequences

- Dependency upgrades, source-tree reorganization, and new distribution or installer work remain separate changes.
- The Gradle build preserves the external-data contract rather than moving GameData into the JAR.
- Maven copies `src/META-INF/MANIFEST.MF` to its classes output but replaces it when packaging the final JAR, so the Maven artifact omits `Main-Class`. Gradle deliberately corrects that mismatch by adding `Main-Class: com.kor.admiralty.ui.AdmiraltyConsole` to its final thin JAR. This supplies launch metadata but does not bundle runtime dependencies or create a standalone distribution.
- The committed Gradle 9.7.1 Wrapper uses the binary distribution, pins its distribution checksum, and requires an installed JDK 25 while declaring a Java 25 toolchain and release target.
- Parity is defined by equivalent dependencies, compiled classes, intended resources, tests, launch behavior, and external-data handling rather than byte-identical JAR output; the deliberate final-manifest correction is documented separately.
- Main resources are limited to non-Java files under `src/com/**`; ignored graph caches below `src/` are not build inputs.
- `Main-Class` is declared directly by the Gradle `jar` task. The obsolete `src/META-INF/MANIFEST.MF` resource is removed so packaging metadata has one source of truth.
- Dependency versions remain directly visible in the single-module Kotlin build script.
- The first Gradle build preserves Maven's `Admiralty:Admiralty:1.0.5-SNAPSHOT` coordinates even though other repository version markers say `1.0.30`; reconciling version sources is separate work.
- Maven's inactive `commons-codec:1.17.2` dependency-management entry remains as a Gradle dependency constraint so the migration does not silently change dependency policy.
- Gradle uses its conventional `build/` output directory. Active operational documentation and code must stop depending on Maven's `target/` layout, while completed historical verification records remain historical.
- Contributor commands use Gradle's native lifecycle tasks rather than Maven-shaped aliases.
- The interactive Ship Filter visual-baseline harness is exposed as a non-headless Java 25 `JavaExec` task using the test runtime classpath, standard `--args`, the project directory as its working directory, and `build/visual-baseline-data` for scratch data.
- OpenRewrite retains recipe version `3.42.1` with an explicitly pinned, resolution-tested Gradle plugin from public repositories. The migration must not introduce authenticated artifact-repository credentials; inability to preserve `rewriteDryRun` under that constraint reopens the decision rather than silently upgrading recipes or adding credentials.
- Migration evidence is recorded in `docs/verification/gradle-migration.md` before `pom.xml` is removed, including commands, semantic artifact comparison, and launch checks.
- The obsolete `target/` ignore is removed after its remaining operational uses are migrated; `.gradle/` and `build/` become the ignored Gradle outputs.
- Maven-specific generated IntelliJ metadata is removed and regenerated through Gradle import; unrelated project settings remain.
- Contributor documentation and operational visual-baseline instructions must use Gradle before the migration is complete.
