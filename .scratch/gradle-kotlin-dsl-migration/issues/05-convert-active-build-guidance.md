Status: resolved
Blocked by: 02, 03, 04

# 05: Convert active contributor and operational guidance

**What to build:** Make every active contributor instruction and current design acceptance criterion describe the verified Gradle workflow, while preserving completed Maven records as truthful historical evidence.

- [x] Active guidance states the installed JDK 25 prerequisite and uses the committed Wrapper for clean builds, complete tests, focused class and method tests, JAR creation, and the native build lifecycle.
- [x] Active OpenRewrite guidance uses the verified Gradle dry-run and applying tasks with an appropriate warning about source mutation.
- [x] Current design acceptance criteria and source documentation no longer attribute active build behavior to Maven or Surefire.
- [x] Active output-path guidance uses the Gradle build directory and does not instruct contributors or tools to recreate Maven-era output paths.
- [x] Completed verification records, dated migration evidence, and historical capture results continue to state the commands and paths that were actually used.
- [x] A repository-wide review classifies every remaining Maven, Surefire, and Maven-output reference as historical evidence, accepted migration context, or deliberately pending final removal.

## Comments

Converted `AGENTS.md`, the public build instructions, current design acceptance criteria, the active Ship Filter reproduction commands, and the visual harness Javadoc to the installed-JDK-25 Gradle Wrapper workflow. The guidance now covers `clean build`, the complete test suite, focused class and method filters, `jar`, `rewriteDryRun`, and the source-mutating `rewriteRun`; active generated paths use `build/` and `build/libs/`.

Verified the documented class and method filters against `GameDataTest` and `BuildHarnessTest.testsRunHeadlessly`, created `build/libs/Admiralty-1.0.5-SNAPSHOT.jar` with `jar`, and ran `rewriteDryRun` successfully without tracked source mutation. A final `.\gradlew.bat clean build` completed all compilation, tests, thin-JAR checks, and exploded and packaged bootstrap verification successfully.

The repository-wide reference review classified the remaining Maven, Surefire, and Maven-output references as follows:

- Historical evidence: the issue 47 and 48 implementation/walkthrough records, `docs/design/ship-filter-retirement-verification.md`, resolved migration tickets 01 through 04, and the explicitly labeled earlier-revision command blocks in `docs/visual-baselines/ship-filter-before/README.md`.
- Accepted migration context: this migration spec and ticket set, ADR-0002, Gradle's `mavenCentral()` repository declaration, and IntelliJ's Maven Central repository name and URLs. These describe the cutover or an artifact repository rather than an active Maven workflow.
- Deliberately pending ticket 07 removal: `pom.xml`, Maven-generated entries in `.idea/compiler.xml` and `.idea/misc.xml`, and `.gitignore`'s obsolete `target/` entry.
