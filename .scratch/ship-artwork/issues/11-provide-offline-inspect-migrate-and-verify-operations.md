Status: ready-for-agent
Blocked by: 09, 10

# 11: Provide offline inspect, migrate, and verify operations

## What to build

Give maintainers a headless operator tool that uses the same archive, identity, migration, and validation implementation as the application while remaining explicit about its target and offline behavior.

## Acceptance criteria

- [ ] A Gradle Java execution task exposes inspect, migrate, and verify operations.
- [ ] Every invocation requires an explicit data-directory option and never applies application directory inference.
- [ ] Inspect inventories legacy and v2 state without writing or invoking remote acquisition.
- [ ] Migrate is offline by default and fails observably if implementation code attempts network access.
- [ ] Verify checks every v2 entry, digest, identity, schema, recipe, and metadata relationship without writing.
- [ ] Human-readable and JSON output are projections of the same structured outcome.
- [ ] Invalid arguments, verification findings, and operational failures use distinct nonzero exit-status categories; unmatched legacy entries are findings.
