Status: ready-for-agent
Blocked by: 06, 11

# 12: Add explicit online refresh and guarded legacy cleanup

## What to build

Complete the operator lifecycle with an intentional online refresh mode and a narrowly targeted cleanup operation that cannot remove rollback state without verified replacement state and explicit confirmation.

## Acceptance criteria

- [ ] Migration performs GitHub acquisition only when the online-refresh option is explicitly supplied.
- [ ] Online refresh uses the same production validation and composition rules as application acquisition.
- [ ] Legacy cleanup refuses to run unless v2 verification succeeds in the same operation context.
- [ ] Legacy cleanup also requires an explicit confirmation flag.
- [ ] Cleanup resolves and validates the exact requested data directory and deletes only its legacy archive.
- [ ] Refused and successful cleanup outcomes have equivalent human and JSON meaning and appropriate exit categories.
- [ ] Automated verification does not claim that a real online refresh occurred.
