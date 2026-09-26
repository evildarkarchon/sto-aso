# Issue tracker: Local Markdown

Issues and specs for this repo live as markdown files in `.scratch/`.

## Conventions

- One feature per directory: `.scratch/<feature-slug>/`
- The spec is `.scratch/<feature-slug>/spec.md`
- Implementation issues are one file per ticket at `.scratch/<feature-slug>/issues/<NN>-<slug>.md`, numbered from `01`, never a single combined tickets file
- Close a completed issue by setting `Status: resolved` and appending the resolution under `## Comments`. Use `wontfix` for work that will not be actioned.
- Comments and conversation history append to the bottom of the file under a `## Comments` heading

### Issue frontmatter

Every implementation issue starts at line 1 with plain, line-oriented frontmatter followed by a blank line and the issue heading:

```text
Status: ready-for-agent
Blocked by: 01, 02

# 03: Example issue title
```

- `Status:` uses a triage role from `triage-labels.md`; new agent-ready implementation tickets use `ready-for-agent`.
- `Blocked by:` contains only comma-and-space-separated, zero-padded ticket numbers from the same feature directory.
- An issue with no prerequisites uses an empty value: `Blocked by:`.
- Keep these fields as plain text rather than bold Markdown so tracker tooling can parse them.

## When a skill says "publish to the issue tracker"

Create the spec at `.scratch/<feature-slug>/spec.md` or an individual ticket at `.scratch/<feature-slug>/issues/<NN>-<slug>.md`, creating directories as needed.

## When a skill says "fetch the relevant ticket"

Read the file at the referenced path. Ticket numbers are scoped to a feature directory; when a bare number matches multiple features, ask which feature is intended. Explicit GitHub links and historical `#<number>` references still identify GitHub items; do not reinterpret them as local ticket numbers.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a file with one **child** file per ticket.

- **Map**: `.scratch/<effort>/map.md` (the Notes / Decisions-so-far / Fog body).
- **Child ticket**: `.scratch/<effort>/issues/NN-<slug>.md`, numbered from `01`, with the question in the body. Prepend a `Type:` line recording the ticket type (`research`/`prototype`/`grilling`/`task`) to the issue frontmatter above. `Status:` records `open`/`claimed`/`resolved` for wayfinding tickets, and new wayfinding tickets start at `Status: open`; ordinary implementation tickets use the triage role strings until resolved.
- **Blocking**: use the `Blocked by:` frontmatter format above. A ticket is unblocked when every file it lists is `resolved`; an empty value is immediately unblocked.
- **Frontier**: scan `.scratch/<effort>/issues/` for files that are open, unblocked, and unclaimed; first by number wins.
- **Claim**: set `Status: claimed` and save before any work.
- **Resolve**: append the answer under an `## Answer` heading, set `Status: resolved`, then append a context pointer (gist + link) to the map's Decisions-so-far in `map.md`.
