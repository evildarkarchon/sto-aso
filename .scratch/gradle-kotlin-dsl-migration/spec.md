Status: ready-for-agent

# Replace Maven with Gradle using Kotlin DSL

## Problem Statement

The project currently uses Maven even though the maintainer personally prefers Gradle and wants new Gradle builds authored with Kotlin DSL, in line with Gradle's recommendation for new builds. The existing Maven configuration also leaves build behavior spread across nonstandard source roots, resource-copy rules, test JVM settings, OpenRewrite integration, manual visual-baseline commands, Maven-specific IDE metadata, and active documentation. A partial conversion would risk missing production resources, changing Java or dependency behavior, breaking headless or subprocess tests, packaging generated graph data, or leaving two build systems that drift apart.

## Solution

Replace Maven atomically with a single-module Gradle 9.7.1 build written in Kotlin DSL. Preserve the existing Java 25 application, dependency versions, source layout, automated tests, thin-JAR boundary, external GameData behavior, and OpenRewrite maintenance workflow. Commit a checksum-pinned Gradle Wrapper, use conventional Gradle lifecycle tasks and output directories, provide a dedicated task for the interactive Ship Filter visual-baseline harness, update active documentation and IDE metadata, and remove Maven only after semantic parity has been verified and recorded.

The Gradle JAR will intentionally correct one Maven packaging mismatch: it will contain main-class metadata declared by the build even though Maven replaced the source manifest and omitted that metadata from its final JAR. The artifact remains thin; it does not bundle runtime dependencies or constitute a standalone distribution.

## User Stories

1. As the project maintainer, I want Gradle to be the sole build system, so that I maintain one authoritative build definition.
2. As the project maintainer, I want the Gradle build written with Kotlin DSL, so that new build logic follows my preferred and Gradle-recommended authoring style.
3. As a contributor, I want a committed Gradle Wrapper, so that I do not need to install or select a compatible Gradle version manually.
4. As a contributor, I want the Wrapper pinned to Gradle 9.7.1, so that local and automated builds use the same Java-25-compatible Gradle release.
5. As a security-conscious contributor, I want the Wrapper distribution checksum pinned, so that Gradle downloads are integrity checked.
6. As a contributor, I want the build to require an installed JDK 25, so that it preserves the project's existing toolchain prerequisite without downloading a JDK unexpectedly.
7. As a contributor, I want compilation to declare both a Java 25 toolchain and release target, so that language features, APIs, and bytecode remain consistently targeted.
8. As a contributor, I want the existing production and test source roots preserved, so that the migration does not mix build conversion with source-tree reorganization.
9. As an application user, I want all bundled Swing images and other intended classpath resources to remain available, so that the interface behaves and renders as before.
10. As a release maintainer, I want generated graph caches below the source tree excluded from build inputs, so that ignored development artifacts cannot leak into a release JAR.
11. As an application user, I want GameData to remain external to the JAR and resolved beside the executable with the existing fallback behavior, so that updates and local player state continue to work correctly.
12. As a release maintainer, I want the JAR to declare the Admiralty console as its main class, so that the artifact contains the launch metadata the source manifest intended.
13. As a release maintainer, I want the artifact to remain a thin JAR, so that the migration does not silently introduce a fat JAR, installer, or distribution format.
14. As a contributor, I want the existing JAXB API and runtime versions preserved, so that Admirals XML continues using the established `javax` namespace and wire format.
15. As a contributor, I want all other production dependency versions preserved, so that dependency upgrades cannot be confused with build-system regressions.
16. As a contributor, I want the inactive Commons Codec version constraint preserved, so that the Gradle build retains the dependency policy expressed by Maven.
17. As a contributor, I want JUnit 5 tests to run on the JUnit Platform, so that the existing test suite remains discoverable and executable.
18. As a contributor, I want automated tests to run with headless AWT enabled, so that Swing-related tests remain reliable without a display.
19. As a contributor, I want tests that compile Java dynamically or launch child JVMs to continue receiving a full JDK and runtime classpath, so that their observable behavior does not change under Gradle.
20. As a contributor, I want focused-test execution documented with native Gradle syntax, so that I can iterate on one test class or method efficiently.
21. As a contributor, I want standard Gradle lifecycle tasks, so that commands use familiar Gradle vocabulary instead of Maven-shaped aliases.
22. As a maintainer, I want the Java 25 OpenRewrite recipe available through native Gradle tasks, so that the documented dry-run and apply workflows remain usable.
23. As a maintainer, I want OpenRewrite to resolve through public artifact repositories, so that the build does not acquire a new credential prerequisite.
24. As a maintainer, I want OpenRewrite recipe behavior preserved at its current version, so that the build migration does not silently upgrade source-transformation rules.
25. As a UI maintainer, I want one Gradle task to run any Ship Filter visual-baseline mode, so that I no longer construct test and dependency classpaths by hand.
26. As a UI maintainer, I want the visual-baseline harness to remain non-headless and use Java 25, so that it can open and exercise real Swing windows and flexible main methods.
27. As a UI maintainer, I want visual-baseline scratch output under the Gradle build directory, so that generated files follow the new build lifecycle.
28. As a contributor, I want active development and verification instructions converted to Gradle, so that copied commands describe the current build accurately.
29. As a maintainer, I want completed verification records preserved as historical evidence, so that past work is not rewritten to pretend it used Gradle.
30. As an IntelliJ user, I want Maven-specific generated project metadata removed, so that the project can be imported from its Gradle model without conflicting configuration.
31. As a contributor, I want Gradle output and local state ignored while obsolete Maven output is no longer hidden, so that stale `target` usage is detectable.
32. As a reviewer, I want Maven and Gradle compared before Maven is removed, so that the cutover is backed by evidence rather than assumption.
33. As a reviewer, I want parity judged semantically rather than by JAR bytes, so that harmless archive timestamps and ordering differences do not cause false failures.
34. As a future maintainer, I want the intentional main-class manifest correction documented separately from parity, so that the sole behavior difference is explicit.
35. As a future maintainer, I want migration commands and results retained in a verification record, so that the reason Maven could safely be removed remains inspectable.

## Implementation Decisions

- Use Gradle because it is the maintainer's personal project choice. Use Kotlin DSL because Gradle recommends it for new builds; do not claim that this preference proves Gradle is intrinsically superior to Maven.
- Use a single-module Java build with project coordinates `Admiralty:Admiralty:1.0.5-SNAPSHOT`. The conflict between that version and other repository markers reporting `1.0.30` is deliberately preserved for separate work.
- Commit the complete Gradle 9.7.1 Wrapper using the binary distribution and an official distribution SHA-256 checksum.
- Require an installed JDK 25. Declare a Java 25 toolchain and an explicit Java 25 release target; do not configure automatic JDK provisioning.
- Preserve the existing nonstandard production and test source roots rather than moving code into Gradle's conventional directory layout.
- Treat non-Java content below the production package hierarchy as main resources. Do not treat the entire source root as a resource directory, and do not package nested graph caches.
- Declare main-class metadata directly in the JAR task and remove the obsolete source manifest. The final artifact remains thin and does not bundle dependencies.
- Keep GameData and per-Admiral state outside the JAR. Preserve the accepted executable-directory selection and working-directory fallback behavior.
- Declare dependency versions directly in the Kotlin build script rather than introducing a version catalog.
- Preserve Jakarta XML Bind API 2.3.3, GlassFish JAXB Runtime 2.3.9, Commons CSV 1.3, SwingX 1.6.4, and txtmark 0.13 as runtime dependencies.
- Preserve Commons Codec 1.17.2 as a dependency constraint without promoting it to a direct dependency.
- Preserve JUnit Jupiter 5.12.2 for tests and declare the matching JUnit Platform launcher explicitly at test runtime.
- Configure the test task to use the JUnit Platform, set headless AWT for test JVMs, run from the project directory, and retain the full test runtime classpath.
- Do not add Maven-compatible lifecycle aliases. The authoritative lifecycle uses Gradle's native clean, test, build, and JAR tasks.
- Preserve OpenRewrite's `UpgradeToJava25` recipe at recipe-library version 3.42.1, datatable export, dry-run task, and applying task.
- Pin an OpenRewrite Gradle plugin version that demonstrably resolves and executes with Gradle 9.7.1 and JDK 25. Use public repositories only. If no compatible public resolution exists, stop and reopen the OpenRewrite decision rather than adding credentials or upgrading the recipe silently.
- Add one non-headless Java execution task for the Ship Filter visual-baseline harness. It uses Java 25, the complete test runtime classpath, the project directory as its working directory, and Gradle's standard argument forwarding.
- Move visual-baseline scratch data from the Maven output tree to the Gradle output tree. Keep user-selected image destinations controlled by the harness arguments.
- Use Gradle's conventional build output directory. Ignore Gradle local state and build output, remove the obsolete Maven output ignore, and ensure no active code recreates Maven-era output paths.
- Update active contributor instructions, current design acceptance criteria, source documentation that attributes behavior to Maven or Surefire, and executable visual-baseline instructions.
- Preserve completed historical verification documents unless they incorrectly claim to describe the current build workflow.
- Remove only Maven-specific generated IntelliJ metadata and retain unrelated project settings. Allow IntelliJ's Gradle import to regenerate its model.
- Add Gradle and verify parity while Maven still exists on the migration branch. Remove Maven before merge so the repository never carries two authoritative builds on its mainline.
- Record the accepted build-system decision separately from the implementation verification. The decision record explains why and scope; the verification record captures observed commands and results.

## Testing Decisions

- Prefer the highest existing seam: a clean top-level Gradle build must compile all production and test sources, process intended resources, and execute the complete JUnit suite successfully on JDK 25.
- Good automated tests assert externally observable build and application contracts. They should not inspect incidental Gradle implementation details, task internals, archive timestamps, or file ordering.
- Run the complete Maven test/package lifecycle before removing Maven, then run the complete Gradle clean/build lifecycle. Record tool versions, commands, outcomes, and any intentional differences.
- Compare resolved first-order dependency coordinates and versions. Confirm that the Commons Codec constraint remains non-direct and that JAXB retains its legacy `javax` API namespace behavior.
- Compare compiled production and test class inventories between build systems. Differences require explanation before Maven removal.
- Compare intended resource inventories semantically. All required UI resources must be present, and generated graph-cache content must be absent.
- Inspect the Gradle JAR manifest for the agreed main class. Record that this differs intentionally from Maven's generated final manifest.
- Confirm the Gradle JAR remains thin and does not unexpectedly embed runtime dependency JARs or GameData.
- Exercise application bootstrap with the existing external GameData layout and verify the accepted executable-directory and working-directory fallback behavior.
- Run the existing build-harness test to prove the Gradle test JVM supplies headless AWT.
- Run tests that use the system Java compiler to prove Gradle selects a full JDK rather than a runtime-only image.
- Run the small-heap Solver subprocess test without adapting its child-JVM mechanism. Its success proves the Gradle test worker exposes a sufficient runtime classpath.
- Run tests that load fixtures both from the classpath and project-relative filesystem locations to prove source-set and working-directory parity.
- Run the OpenRewrite dry-run task using only public repositories. Confirm that the Java 25 recipe activates, produces its normal report or patch output, and does not require credentials.
- Do not run the applying OpenRewrite task merely as migration verification, because it mutates source. Its task configuration and dry-run behavior provide the safe acceptance seam.
- Compile and invoke the visual-baseline task in a non-headless Windows session. Exercise at least one image-producing mode and the interaction-smoke mode using normal task arguments.
- Confirm visual-baseline generated data and default example outputs use the Gradle build tree and that no active workflow recreates `target`.
- Verify focused-test documentation against an existing test class using Gradle's test filtering syntax.
- Validate that the committed Wrapper starts from a clean checkout, uses Gradle 9.7.1, and accepts the configured distribution checksum.
- Treat the existing JUnit suite, build-harness test, XML compatibility tests, application-bootstrap tests, GameData tests, Solver subprocess test, architecture test, and visual-baseline harness as prior art. Add new tests only where none of those seams can prove an agreed contract.
- Store concise semantic comparison and manual-smoke results in the migration verification record; do not commit generated binaries, dependency caches, build reports, or baseline scratch data as evidence.

## Out of Scope

- Upgrading production or test dependencies.
- Migrating JAXB source imports or the Admirals XML wire format to Jakarta JAXB 3 or later.
- Reorganizing production, test, fixture, or resource directories into Gradle's conventional layout.
- Reconciling the `1.0.5-SNAPSHOT` project version with the `1.0.30` tag and build-information file.
- Creating a fat or shaded JAR, dependency bundle, application distribution, installer, packaged EXE, runtime image, or release-publication pipeline.
- Moving GameData into the JAR or changing GameData Refresh, Admiral persistence, icon-cache, or executable-directory semantics.
- Adding automatic JDK downloads, CI workflows, dependency locking, dependency verification metadata, version catalogs, convention plugins, composite builds, or additional modules.
- Adding authenticated artifact repositories or repository credentials for OpenRewrite.
- Running OpenRewrite transformations against production source as part of the build migration.
- Rewriting completed historical verification records solely to replace Maven terminology.
- Refactoring production Java, changing flexible main-method visibility, or changing tests whose behavior already passes under Gradle.
- Requiring byte-for-byte equality between Maven and Gradle artifacts.

## Further Notes

- This specification follows the accepted decision "Use Gradle with Kotlin DSL as the sole build system."
- Maven's final JAR currently omits the main class even though a source manifest declares it. The Gradle manifest is the one intentional packaging correction and must remain conspicuous in review and verification.
- The existing resource rule is broader than the intended application resource boundary and can include ignored generated graph data. The Gradle resource configuration must express intent rather than transliterate that accidental breadth.
- Gradle 9.7.1 has been probed successfully with the subprocess test's use of the test JVM classpath; no test rewrite or manual classpath system property is expected.
- Current OpenRewrite publication and repository transitions make resolution testing mandatory before selecting the plugin version. Recipe upgrades and credentials are not acceptable fallbacks under this specification.
- Documentation work is part of completion, not follow-up cleanup, because several active commands and the visual-baseline procedure are executable operational interfaces.
