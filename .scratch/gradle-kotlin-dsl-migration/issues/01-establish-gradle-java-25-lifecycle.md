Status: resolved
Blocked by:

# 01: Establish a green Gradle 9.7.1 Java 25 lifecycle

**What to build:** Give contributors a checksum-pinned Gradle Wrapper and Kotlin DSL build that can compile and test the existing Admiralty application on an installed JDK 25 while Maven remains available as the migration reference.

- [x] The committed Wrapper uses the Gradle 9.7.1 binary distribution and verifies it with the official SHA-256 checksum.
- [x] The single-module build preserves the existing project coordinates, production and test source roots, test fixtures, and intended non-Java application resources without admitting generated graph caches.
- [x] Compilation selects an installed Java 25 toolchain and explicitly targets Java 25 without configuring automatic JDK provisioning.
- [x] Production and test dependencies retain their existing versions, including the legacy JAXB API/runtime pair, while Commons Codec 1.17.2 remains a constraint rather than a direct dependency.
- [x] JUnit 5 runs on the JUnit Platform with the matching launcher, headless AWT, the project working directory, and the complete test runtime classpath.
- [x] The complete test suite passes, including the existing checks for headless AWT, the system Java compiler, child-JVM classpath behavior, JAXB compatibility, and project-relative fixtures.
- [x] Gradle's native filtering syntax successfully runs an existing test class and an existing test method.
- [x] Current build-harness documentation describes the build-neutral contract accurately rather than attributing it to Maven.

## Comments

Implemented a checksum-pinned Gradle 9.7.1 Wrapper and single-module Kotlin DSL Java 25 lifecycle while retaining Maven as the migration reference. The build selects an installed JDK 25 with toolchain auto-download disabled, targets Java 25 bytecode, preserves dependency versions and source roots, and limits production resources to the 206 non-Java files under `src/com/**` so nested Graphify caches are excluded.

Verified `gradlew.bat clean build` with 295 tests, zero failures, errors, or skips. Native filters passed for `com.kor.admiralty.io.GameDataTest` and `com.kor.admiralty.BuildHarnessTest.testsRunHeadlessly`; targeted compiler, child-JVM, JAXB, classpath-fixture, and project-relative resource seams also passed.
