Status: ready-for-agent
Blocked by: 01

# 03: Restore the Java 25 OpenRewrite workflow

**What to build:** Give maintainers native Gradle tasks for previewing and applying the established Java 25 OpenRewrite recipe without adding repository credentials or silently changing recipe behavior.

- [ ] An explicitly pinned OpenRewrite Gradle plugin resolves and executes with Gradle 9.7.1 and JDK 25 using public artifact repositories only.
- [ ] The active recipe remains `UpgradeToJava25` from recipe-library version 3.42.1 and datatable export remains enabled.
- [ ] The native dry-run task activates the recipe and produces its normal report or patch output without modifying tracked source.
- [ ] The native applying task is available but is not run merely to verify the migration.
- [ ] No authenticated repository, credential prerequisite, or silent recipe-library upgrade is introduced.
- [ ] If compatible public resolution cannot satisfy these criteria, implementation stops and the accepted OpenRewrite decision is reopened.
