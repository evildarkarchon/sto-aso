# Issue tracker: GitHub

Issues and specs for this repo live in GitHub Issues at `evildarkarchon/sto-aso`. Use the `gh` CLI for all operations. Existing `.scratch/` files are historical records; create GitHub issues for new work.

Explicit `.scratch/...` paths still refer to those historical files. Their feature-scoped ticket numbers are not GitHub issue numbers.

## Conventions

- Use `-R evildarkarchon/sto-aso` with `gh issue` and `gh pr` commands so operations target this repo, not its `upstream` remote.
- **Create an issue**: `gh issue create -R evildarkarchon/sto-aso --title "..." --body-file <path>`. Write multiline bodies to a temporary UTF-8 file first.
- **Read an issue**: `gh issue view <number> -R evildarkarchon/sto-aso --comments`; include labels when collecting ticket details.
- **List issues**: `gh issue list -R evildarkarchon/sto-aso --state open --json number,title,body,labels,comments`, with appropriate `--label`, `--state`, and `--limit` filters.
- **Comment on an issue**: `gh issue comment <number> -R evildarkarchon/sto-aso --body-file <path>`.
- **Apply / remove labels**: `gh issue edit <number> -R evildarkarchon/sto-aso --add-label "..."` / `--remove-label "..."`. Use `docs/agents/triage-labels.md` for triage roles.
- **Close**: `gh issue close <number> -R evildarkarchon/sto-aso --comment "..."`.

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests; `/triage` reads this flag.)_

When set to `yes`, PRs run through the same labels and states as issues, using the `gh pr` equivalents:

- **Read a PR**: `gh pr view <number> -R evildarkarchon/sto-aso --comments` and `gh pr diff <number> -R evildarkarchon/sto-aso`.
- **List external PRs for triage**: `gh pr list -R evildarkarchon/sto-aso --state open --json number,title,body,labels,author,authorAssociation,comments`; keep `CONTRIBUTOR`, `FIRST_TIME_CONTRIBUTOR`, and `NONE`.
- **Comment / label / close**: use `gh pr comment`, `gh pr edit`, and `gh pr close`.

GitHub shares one number space across issues and PRs. For a bare `#42`, try `gh pr view 42` and then `gh issue view 42`.

## When a skill says "publish to the issue tracker"

Create a GitHub issue.

## When a skill says "fetch the relevant ticket"

Run `gh issue view <number> -R evildarkarchon/sto-aso --comments`.

## Wayfinding operations

Used by `/wayfinder`. The **map** is one issue with **child** issues as tickets.

- **Map**: one issue labelled `wayfinder:map`, holding Notes / Decisions-so-far / Fog.
- **Child ticket**: link an issue to the map as a GitHub sub-issue. If sub-issues are unavailable, add it to a task list in the map and put `Part of #<map>` at the top of the child body. Label it `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`).
- **Blocking**: use native issue dependencies. Add `blocked_by` with `gh api --method POST repos/evildarkarchon/sto-aso/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`, where the ID comes from `gh api repos/evildarkarchon/sto-aso/issues/<n> --jq .id`. If dependencies are unavailable, use a `Blocked by: #<n>` line in the child body.
- **Frontier**: among the map's open children, choose the first in map order with no open blocker and no assignee.
- **Claim**: `gh issue edit <n> -R evildarkarchon/sto-aso --add-assignee @me`.
- **Resolve**: comment with the answer, close the child, then append a context pointer (gist + link) to the map's Decisions-so-far.
