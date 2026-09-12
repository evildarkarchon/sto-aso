Status: ready-for-agent
Blocked by: 02, 03, 04, 05

# 06: Record Maven-to-Gradle semantic parity

**What to build:** Give reviewers a durable verification record showing why Gradle can replace Maven based on observable build and application contracts rather than byte-for-byte archive equality.

- [ ] The record identifies the observed JDK, Maven, and Gradle versions and the exact commands and outcomes used for comparison.
- [ ] A complete Maven test/package lifecycle succeeds while Maven is still present, followed by a complete clean Gradle build.
- [ ] First-order dependency coordinates and versions match, Commons Codec remains non-direct, and the legacy JAXB namespace and XML behavior are confirmed.
- [ ] Production classes, test classes, intended resources, excluded graph-cache content, and test fixtures are compared semantically with every difference explained.
- [ ] The Gradle JAR is inspected for thinness, external GameData, intended resources, and the agreed main-class metadata; the manifest correction is recorded separately from parity.
- [ ] The record covers executable-directory and working-directory GameData behavior, headless tests, the full JDK compiler, the small-heap child JVM, classpath and filesystem fixtures, and focused-test syntax.
- [ ] The Wrapper version and checksum are exercised from a clean checkout without automatic JDK provisioning.
- [ ] The OpenRewrite dry run, an image-producing visual mode, and the interaction-smoke mode are executed and their results recorded.
- [ ] Generated binaries, dependency caches, build reports, and visual scratch data are not committed as evidence.
