Status: resolved
Blocked by: 06

# 07: Retire Maven and finalize repository metadata

**What to build:** Complete the atomic cutover so Gradle is the repository's sole authoritative build, with obsolete Maven configuration removed and repository metadata aligned to Gradle's outputs.

- [x] The Maven build definition is removed only after the semantic parity record is complete.
- [x] The obsolete source manifest is removed after the Gradle JAR has become the single source of main-class metadata.
- [x] Only Maven-generated IntelliJ project metadata is removed or contracted; unrelated project settings remain intact.
- [x] Gradle local state and build output are ignored, while Maven output is no longer ignored so stale usage remains visible.
- [x] No active source, build configuration, contributor instruction, or operational workflow depends on Maven, Surefire, or Maven-era output directories.
- [x] The committed Wrapper remains usable from a clean checkout with an installed JDK 25.
- [x] A final clean Gradle build and representative packaged-bootstrap smoke succeed after Maven removal.
- [x] The repository contains one authoritative build system and no Maven-shaped lifecycle aliases.

## Comments

Removed `pom.xml` after the completed semantic parity record in
`docs/verification/gradle-migration.md`, and retained Gradle's direct JAR manifest
configuration as the sole main-class metadata source. Removed the Maven-only
`.idea/compiler.xml` file and contracted `.idea/misc.xml` by deleting only its
`MavenProjectsManager` component; unrelated IntelliJ settings remain unchanged.

Removed the obsolete `target/` ignore and deleted the existing generated,
untracked Maven output tree so future Maven-era output is visible. A repository
audit found no active Maven, Surefire, or `target/` workflow references and no
Maven-shaped Gradle lifecycle aliases; historical verification and migration
records remain intact.

Verified the committed Wrapper as Gradle 9.7.1 on Temurin JDK 25.0.4.1.
`.\gradlew.bat clean build verifyPackagedBootstrap --console=plain` completed all
12 tasks successfully, including 295 tests with zero failures, errors, or skips,
thin-JAR verification, and both exploded and packaged bootstrap probes.
