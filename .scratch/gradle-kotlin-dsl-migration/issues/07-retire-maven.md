Status: ready-for-agent
Blocked by: 06

# 07: Retire Maven and finalize repository metadata

**What to build:** Complete the atomic cutover so Gradle is the repository's sole authoritative build, with obsolete Maven configuration removed and repository metadata aligned to Gradle's outputs.

- [ ] The Maven build definition is removed only after the semantic parity record is complete.
- [ ] The obsolete source manifest is removed after the Gradle JAR has become the single source of main-class metadata.
- [ ] Only Maven-generated IntelliJ project metadata is removed or contracted; unrelated project settings remain intact.
- [ ] Gradle local state and build output are ignored, while Maven output is no longer ignored so stale usage remains visible.
- [ ] No active source, build configuration, contributor instruction, or operational workflow depends on Maven, Surefire, or Maven-era output directories.
- [ ] The committed Wrapper remains usable from a clean checkout with an installed JDK 25.
- [ ] A final clean Gradle build and representative packaged-bootstrap smoke succeed after Maven removal.
- [ ] The repository contains one authoritative build system and no Maven-shaped lifecycle aliases.
