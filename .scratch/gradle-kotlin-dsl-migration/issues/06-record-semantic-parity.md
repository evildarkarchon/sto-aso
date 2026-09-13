Status: resolved
Blocked by: 02, 03, 04, 05

# 06: Record Maven-to-Gradle semantic parity

**What to build:** Give reviewers a durable verification record showing why Gradle can replace Maven based on observable build and application contracts rather than byte-for-byte archive equality.

- [x] The record identifies the observed JDK, Maven, and Gradle versions and the exact commands and outcomes used for comparison.
- [x] A complete Maven test/package lifecycle succeeds while Maven is still present, followed by a complete clean Gradle build.
- [x] First-order dependency coordinates and versions match, Commons Codec remains non-direct, and the legacy JAXB namespace and XML behavior are confirmed.
- [x] Production classes, test classes, intended resources, excluded graph-cache content, and test fixtures are compared semantically with every difference explained.
- [x] The Gradle JAR is inspected for thinness, external GameData, intended resources, and the agreed main-class metadata; the manifest correction is recorded separately from parity.
- [x] The record covers executable-directory and working-directory GameData behavior, headless tests, the full JDK compiler, the small-heap child JVM, classpath and filesystem fixtures, and focused-test syntax.
- [x] The Wrapper version and checksum are exercised from a clean checkout without automatic JDK provisioning.
- [x] The OpenRewrite dry run, an image-producing visual mode, and the interaction-smoke mode are executed and their results recorded.
- [x] Generated binaries, dependency caches, build reports, and visual scratch data are not committed as evidence.

## Comments

Recorded the completed Maven-to-Gradle semantic comparison in
`docs/verification/gradle-migration.md`. Maven `clean package` and Gradle
`clean build` each ran 295 tests successfully; class and fixture inventories matched, and
Maven's 315 extra resources were exclusively generated Graphify cache files.

The verification also confirmed dependency and legacy JAXB parity, thin-JAR and
external GameData behavior, both GameData path rules, focused headless/compiler/
small-heap/fixture seams, the cold checksum-pinned Wrapper with JDK auto-download
disabled, OpenRewrite dry-run behavior, a pixel-identical image capture, and the
complete native interaction smoke.

A source-only Windows checkout exposed that the GameData fixture CSVs lacked the
LF rule already applied to production GameData. The same digest-sensitive test
failed under Maven and Gradle, proving the defect was build-independent. The
fixture path is now pinned to LF in `.gitattributes`; a new source-only snapshot
then completed the full Gradle build with all 295 tests and verification tasks.
