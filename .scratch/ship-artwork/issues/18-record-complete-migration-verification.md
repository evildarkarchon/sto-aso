Status: ready-for-agent
Blocked by: 12, 17

# 18: Record complete migration verification

## What to build

Produce the final evidence that the Ship Artwork migration works across application, persistence, operator, visual, architectural, and real-network paths without conflating automated coverage with manual online verification.

## Acceptance criteria

- [ ] The clean Java 25 Gradle build, complete tests, architecture checks, and build-artifact verification pass.
- [ ] Offline inspect, migrate, and verify operations pass against representative legacy fixtures and prove their write and network restrictions.
- [ ] Retained visual baselines pass for reusable Roster, One-Time Ship, Starship Trait, selection, Ship usage, and Solution presentations.
- [ ] Restart-visible checks demonstrate v2 persistence, legacy preservation, corruption recovery, and final flush behavior.
- [ ] A real explicit online refresh is performed and recorded separately from automated verification, including its environment and outcome.
- [ ] Any visible pixel or timing change is accompanied by before-and-after evidence and reopened for decision if it alters layout, filtering, selection, or card structure.
- [ ] The verification record confirms that no compatibility interface or alternate Ship Artwork implementation remains.
