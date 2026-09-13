# Repository Guidelines

## Project Structure & Module Organization

ASO is a Java 25 Swing desktop application. Production code lives under `src/com/kor/admiralty`; domain models are in `beans`, persistence and parsers in `io`, scoring behavior in `rules` and `rewards`, and desktop presentation in `ui`. Bundled images and other classpath assets live in `src/com/kor/admiralty/ui/resources`. Runtime reference CSVs and their digest manifest are in `data/`. Tests mirror production packages under `test/com/kor/admiralty`, with fixtures in `test/resources`. Architecture notes, ADRs, and contributor-facing agent references live in `docs/`. Gradle output belongs in `build/`.

## Build, Test, and Development Commands

Use an installed JDK 25 and the committed Gradle Wrapper from PowerShell.

- `.\gradlew.bat clean build` — rebuild from scratch and run the complete Gradle lifecycle, including tests and verification.
- `.\gradlew.bat test` — run the complete JUnit suite.
- `.\gradlew.bat test --tests "com.kor.admiralty.io.GameDataTest"` — run one test class during iteration.
- `.\gradlew.bat test --tests "com.kor.admiralty.BuildHarnessTest.testsRunHeadlessly"` — run one test method.
- `.\gradlew.bat jar` — create the thin application JAR in `build/libs/`.
- `.\gradlew.bat rewriteDryRun` — preview the configured Java 25 OpenRewrite migration. Use `.\gradlew.bat rewriteRun` only when intentionally applying the recipe because it mutates source files.

## Coding Style & Naming Conventions

Follow the surrounding Java style: four-space indentation, braces on the declaration line, and one public top-level type per file. Use `PascalCase` for classes and records, `camelCase` for methods and variables, and `UPPER_SNAKE_CASE` for constants. Keep package names lowercase beneath `com.kor.admiralty`. No formatter or linter is configured, so preserve local import ordering and layout. Preserve accurate comments; document non-obvious reasoning, and add concise Javadoc to new or substantially rewritten methods.

## Testing Guidelines

Tests use JUnit 5 through Gradle's `Test` task in headless AWT mode. Name classes `*Test.java` and test methods after observable behavior, such as `readingBeforeBootstrapThrows`. Add regression tests beside the affected package and prefer the small fixtures in `test/resources` over network or user-state dependencies. `ArchitectureTest` enforces package boundaries. There is no configured coverage threshold; meaningful behavioral coverage and a green `.\gradlew.bat clean build` are required.

## Commit & Pull Request Guidelines

Recent history favors imperative Conventional Commit subjects: `feat(ui): ...`, `fix(data): ...`, `refactor(build): ...`, and `test: ...`. Keep each commit focused. Pull requests should explain what changed and why, link the relevant issue, list verification commands and results, and include before/after screenshots for visible Swing changes. Call out data-format, XML compatibility, or architecture-boundary effects explicitly.

## Repository Context

Use the canonical domain terms in `CONTEXT.md`; `docs/agents/domain.md` explains when that glossary applies. Before creating, reading, updating, or closing local Markdown issues, read `docs/agents/issue-tracker.md`; use `docs/agents/triage-labels.md` when assigning triage status. Preserve LF endings for `data/*.csv` and `data/hashes.md5` as required by `.gitattributes`.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
