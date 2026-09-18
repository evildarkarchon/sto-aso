Status: resolved
Blocked by: 01

# 02: Make complete Solution projection own Ship-card presentation

**What to build:** Make complete Solution projection the Assignment editor's only declared operation for presenting or clearing calculated totals and assigned Ship cards. Remove the public operations that coordinate individual Ship slots or separately clear Solutions and Ship cards, while preserving the same visible presentation and failure ordering.

**Blocked by:** 01: Rebase focused tests onto the narrow numeric-control seam.

- [x] The first-, second-, and third-Ship presentation operations are no longer declared public operations and have no deprecated forwarders, compatibility adapters, or alternate public replacements.
- [x] The separate public Solution-clearing and Ship-clearing operations are no longer declared public operations and have no deprecated forwarders, compatibility adapters, or alternate public replacements.
- [x] Projecting a complete non-null Solution still renders its calculated totals, score, and all three exact Roster cards.
- [x] Projecting a null Solution still clears retained Solution state and all three Ship cards while rendering the bound Assignment totals exactly as before.
- [x] Reversible Assignment unbinding and workspace-level Solution clearing continue to use complete projection paths without exposing internal Ship-slot coordination.
- [x] Off-thread and unbound complete-Solution projection failures occur before any partial presentation change.
- [x] Focused Assignment editor tests and Admiral workspace tests covering Solution presentation, invalidation, navigation, and deployment pass.

## Comments

Removed the five partial public presentation operations and kept their necessary coordination in private helpers owned by complete Solution projection. Added an architecture guard against restoring the retired names.

Verification passed with focused `AssignmentPanelTest`, `ArchitectureTest`, `AdmiralPanelTest`, and `AdmiralWorkspaceHostTest` runs, followed by `./gradlew.bat clean build`.
