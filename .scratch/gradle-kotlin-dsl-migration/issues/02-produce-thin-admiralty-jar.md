Status: ready-for-agent
Blocked by: 01

# 02: Produce the intended thin Admiralty JAR with external GameData

**What to build:** Produce a Gradle JAR that launches the Admiralty console with the intended bundled UI resources while keeping dependencies, GameData, and personal Admiral state external and preserving the established data-directory fallback behavior.

- [ ] The JAR manifest declares the Admiralty console as the main class directly from the Gradle build.
- [ ] The artifact contains every intended non-Java application resource and excludes generated graph caches, test fixtures, GameData, nested dependency JARs, and dependency classes.
- [ ] The artifact remains a thin JAR and no application distribution, installer, runtime image, or dependency bundle is introduced.
- [ ] Production and test class inventories match the Maven reference or every semantic difference is explained.
- [ ] Existing classpath resource and legacy JAXB XML compatibility tests pass unchanged under Gradle.
- [ ] Application bootstrap works from exploded Gradle classes and from the packaged JAR when GameData is beside the executable.
- [ ] When executable-adjacent GameData is absent, application bootstrap retains the established working-directory fallback.
- [ ] The intentional main-class manifest correction is kept conspicuous and is not misreported as Maven parity.
