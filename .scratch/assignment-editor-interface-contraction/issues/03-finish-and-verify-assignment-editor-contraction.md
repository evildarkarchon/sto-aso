Status: resolved
Blocked by: 02

# 03: Finish and verify the Assignment editor contraction

**What to build:** Complete the Assignment editor's ownership boundary without changing visible Swing behavior. Leave exactly construction, binding-state inspection, immutable Assignment projection with its synchronous intent owner, and complete Solution projection as declared public operations; internalize all presentation machinery and record and verify the resulting contract across the Admiral workspace.

**Blocked by:** 02: Make complete Solution projection own Ship-card presentation.

- [x] The Assignment editor is final and declares exactly the four supported public operations: construction, binding-state inspection, immutable Assignment projection, and complete Solution projection.
- [x] The editor-level Assignment-clearing operation is removed without a deprecated forwarder, compatibility adapter, or replacement; Assignment clearing remains owned by the workspace workflow.
- [x] The unused public color constant is removed, and the minimum and maximum critical-chance values are private implementation constants.
- [x] Retained Assignment and Solution projections, callbacks, controls, renderers, labels, and formatting state are private except for the approved package-private numeric-control test seam.
- [x] Design-time and runtime initialization hooks are internal implementation details while preserving their established behavior.
- [x] Focus selection is implemented privately with the same deferred select-all behavior, and the editor no longer advertises a public FocusListener role; the intentional focus-lost no-op remains documented.
- [x] Projection suppression, synchronous intent delivery, authoritative owner reprojection, failure ordering, reversible unbinding, and disposal ordering retain their reasoning comments and concise method documentation.
- [x] Architecture guards prove finality and absence of all six retired declared public operation names without depending on private helper names, fields, listener-class names, or Swing layout.
- [x] Existing architecture protection against mutable Assignment retention, direct mutable binding, and the model PropertyChangeListener role continues to pass.
- [x] Admiral workspace integration coverage passes for manual numeric entry, Assignment and Event selection, correct Admiral ownership, Solution invalidation, deployment, Assignment clearing, Assignment-count changes, disposal, and repeated disposal.
- [x] The Assignment editor design record supersedes the earlier decision to retain the six operations and identifies that retention as a prior change-envelope limit rather than an architectural constraint.
- [x] Focused Assignment editor, architecture, and Admiral workspace test classes pass, followed by the full clean Gradle build on JDK 25.
- [x] The repository graph is refreshed after code changes; the agent-generated focused Swing walkthrough is waived because this application is not exposed through the available computer-use surface.
- [x] Any comment or Javadoc removed or rewritten with the retired contract is reported in the implementation handoff; no screenshot is required unless a visible behavior change is discovered and the behavior decision is reopened.

## Comments

### Automated verification

The Assignment editor is final and its declared public surface is limited to one
constructor plus `hasAssignmentView`, `setAssignmentView`, and
`setAssignmentSolution`. The six retired public operations are absent, retained
presentation state is private, and the existing package-private numeric-control
test seam remains unchanged. Added workspace integration coverage for the Clear
Assignments action, including active-slot clearing, inactive-slot preservation,
authoritative reprojection, and Solution/card removal.

Focused `AssignmentPanelTest`, `ArchitectureTest`, `AdmiralPanelTest`, and
`AdmiralWorkspaceHostTest` runs passed. `./gradlew.bat clean build` passed its
full JUnit and verification lifecycle on JDK 25: 297 tests, 0 failures, errors,
or skips. `codegraph sync .` completed and `codegraph status .` reported the
repository index up to date. The final two-axis review against `a2a4023` found no
standards or specification issues.

### Comment and Javadoc changes

Removed the `clearAssignment` Javadoc with its retired operation and removed the
public focus-callback and helper declarations with the listener role they
described. Added a concise private-listener comment explaining why select-all is
deferred and retained an explicit focus-lost no-op explanation. Existing
projection-suppression, synchronous-owner, reversible-unbinding, failure-order,
and disposal-order documentation remains intact. Rewrote the Assignment editor
design record where its earlier change envelope had required retaining the six
now-retired operations.

### Visible Swing walkthrough waiver

The isolated production `AdmiralPanel` harness compiled and opened a visible
workspace, but this Codex session exposed no native Windows control surface: the
computer-use inventory contained browser tabs only and native app APIs were
disabled. The harness was stopped and removed without interacting with the
workspace, and no programmatic interaction is being reported as a walkthrough.
The user explicitly waived this agent-generated walkthrough requirement because
the application is not supported by the available computer-use surface, so it
does not block resolution.
