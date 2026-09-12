Status: ready-for-agent
Blocked by: 02, 03, 04

# 05: Convert active contributor and operational guidance

**What to build:** Make every active contributor instruction and current design acceptance criterion describe the verified Gradle workflow, while preserving completed Maven records as truthful historical evidence.

- [ ] Active guidance states the installed JDK 25 prerequisite and uses the committed Wrapper for clean builds, complete tests, focused class and method tests, JAR creation, and the native build lifecycle.
- [ ] Active OpenRewrite guidance uses the verified Gradle dry-run and applying tasks with an appropriate warning about source mutation.
- [ ] Current design acceptance criteria and source documentation no longer attribute active build behavior to Maven or Surefire.
- [ ] Active output-path guidance uses the Gradle build directory and does not instruct contributors or tools to recreate Maven-era output paths.
- [ ] Completed verification records, dated migration evidence, and historical capture results continue to state the commands and paths that were actually used.
- [ ] A repository-wide review classifies every remaining Maven, Surefire, and Maven-output reference as historical evidence, accepted migration context, or deliberately pending final removal.
