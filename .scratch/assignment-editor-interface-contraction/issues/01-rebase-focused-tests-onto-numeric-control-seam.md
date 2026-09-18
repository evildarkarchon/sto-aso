Status: resolved
Blocked by:

# 01: Rebase focused tests onto the narrow numeric-control seam

**What to build:** Preserve focused coverage of the Assignment editor's real Swing interaction and owner protocol while removing test dependencies on subclass hooks, retained Assignment or Solution state, Ship renderers, labels, callback ownership, and the six operations scheduled for retirement. Give package-local tests only the minimum seam needed to drive and read the seven numeric Assignment and Event controls.

**Blocked by:** None (can start immediately).

- [x] A package-private test seam exposes only the values of the seven numeric Assignment and Event controls and allows tests to change those values through the real Swing controls.
- [x] The seam exposes no retained Assignment, retained Solution, Ship renderer, label, callback, direct intent-publication operation, or general control registry.
- [x] Focused tests prove that projection is silent and that an accepted control edit reports one complete immutable Assignment intent synchronously on Swing's event-dispatch thread.
- [x] Focused tests prove that authoritative owner reprojection governs subsequent edits and that an owner which does not reproject leaves subsequent edits based on the last authoritative projection.
- [x] Focused tests prove reversible unbinding, release of the former callback owner and retained Solution, frozen control presentation, and exclusive delivery to a later owner after rebinding.
- [x] Focused tests prove that invalid callback binding preserves the prior view, visible controls, and callback owner.
- [x] Constructor dependency validation, surviving-operation event-thread failures, and unbound complete-Solution projection failures remain covered without an anonymous subclass probe.
- [x] Complete Solution projection and clearing are asserted through recursive visible presentation rather than retained Solution or Ship-renderer state, component positions, or layout.
- [x] Tests of individual Ship-slot setters, editor-level Assignment clearing, and the distinction between clearing Ships and clearing Solutions are removed.
- [x] The focused Assignment editor test class passes with no visible Swing behavior change.

## Comments

Implemented a package-private numeric-control wrapper that snapshots and drives only the seven real Assignment and Event formatted fields. Rebased the focused editor tests onto that seam and recursive visible presentation, removing dependencies on subclass hooks, retained state, named renderers, and all six retiring operations.

Verification passed with the focused `AssignmentPanelTest`, the focused architecture and Admiral workspace tests, and `./gradlew.bat clean build`.
