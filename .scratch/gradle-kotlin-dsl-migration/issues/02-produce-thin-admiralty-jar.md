Status: resolved
Blocked by: 01

# 02: Produce the intended thin Admiralty JAR with external GameData

**What to build:** Produce a Gradle JAR that launches the Admiralty console with the intended bundled UI resources while keeping dependencies, GameData, and personal Admiral state external and preserving the established data-directory fallback behavior.

- [x] The JAR manifest declares the Admiralty console as the main class directly from the Gradle build.
- [x] The artifact contains every intended non-Java application resource and excludes generated graph caches, test fixtures, GameData, nested dependency JARs, and dependency classes.
- [x] The artifact remains a thin JAR and no application distribution, installer, runtime image, or dependency bundle is introduced.
- [x] Production and test class inventories match the Maven reference or every semantic difference is explained.
- [x] Existing classpath resource and legacy JAXB XML compatibility tests pass unchanged under Gradle.
- [x] Application bootstrap works from exploded Gradle classes and from the packaged JAR when GameData is beside the executable.
- [x] When executable-adjacent GameData is absent, application bootstrap retains the established working-directory fallback.
- [x] The intentional main-class manifest correction is kept conspicuous and is not misreported as Maven parity.

## Comments

Configured the Gradle JAR manifest directly with `Main-Class: com.kor.admiralty.ui.AdmiraltyConsole` and removed the obsolete source manifest. Added `check` lifecycle verification for the exact intended resource inventory, thin-JAR boundary, and isolated child-JVM bootstrap probes covering both exploded classes with working-directory fallback and the packaged JAR with executable-adjacent external GameData. Runtime dependencies are supplied externally to the packaged probe and are not copied into the artifact.

Verified `mvn clean package` and `gradlew.bat clean build`; both ran 295 tests with zero failures, errors, or skips. Maven and Gradle produced identical inventories of 259 production classes and 80 test classes. Gradle packaged all 206 intended application resources and no extras; Maven's additional 315 resources were exclusively generated `graphify-out` cache files that the migration deliberately excludes. The Gradle-only final-JAR main-class metadata remains the separately documented intentional correction rather than a parity claim.
