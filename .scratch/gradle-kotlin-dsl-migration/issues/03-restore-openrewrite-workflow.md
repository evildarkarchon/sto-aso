Status: resolved
Blocked by: 01

# 03: Restore the Java 25 OpenRewrite workflow

**What to build:** Give maintainers native Gradle tasks for previewing and applying the established Java 25 OpenRewrite recipe without adding repository credentials or silently changing recipe behavior.

- [x] An explicitly pinned OpenRewrite Gradle plugin resolves and executes with Gradle 9.7.1 and JDK 25 using public artifact repositories only.
- [x] The active recipe remains `UpgradeToJava25` from recipe-library version 3.42.1 and datatable export remains enabled.
- [x] The native dry-run task activates the recipe and produces its normal report or patch output without modifying tracked source.
- [x] The native applying task is available but is not run merely to verify the migration.
- [x] No authenticated repository, credential prerequisite, or silent recipe-library upgrade is introduced.
- [x] If compatible public resolution cannot satisfy these criteria, implementation stops and the accepted OpenRewrite decision is reopened.

## Comments

Pinned `org.openrewrite.rewrite` 7.39.0, which aligns its Kotlin runtime with Gradle 9.7, and kept `org.openrewrite.recipe:rewrite-migrate-java` at 3.42.1. The build activates `org.openrewrite.java.migrate.UpgradeToJava25` and enables datatable export through the native OpenRewrite Gradle DSL while retaining only the Gradle Plugin Portal and Maven Central for resolution.

Verified `gradlew.bat rewriteDryRun --refresh-dependencies` on Gradle 9.7.1 and JDK 25. It parsed the project, ran the configured recipe, reported eight files with prospective changes, wrote `build/reports/rewrite/rewrite.patch` and timestamped datatables, and did not modify tracked production or test sources. `gradlew.bat dependencyInsight --dependency rewrite-migrate-java --configuration rewrite` resolved exactly 3.42.1, and `gradlew.bat tasks --all` listed both `rewriteDryRun` and the intentionally unexecuted `rewriteRun` task.
