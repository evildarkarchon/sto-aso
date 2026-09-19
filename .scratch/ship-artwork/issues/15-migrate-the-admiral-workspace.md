Status: ready-for-agent
Blocked by: 14

# 15: Migrate the Admiral workspace

## What to build

Use the application-owned Ship Artwork instance throughout the Admiral workspace so every Roster, selection, Assignment, Solution, and Starship Trait surface receives live artwork without changing domain behavior.

## Acceptance criteria

- [ ] The workspace receives Ship Artwork through constructor ownership seams and does not import process-global application state.
- [ ] Reusable Roster cards request specific presentation with fallback, while One-Time Ship cards retain generic presentation.
- [ ] Assignment and Solution slots preserve the presentation semantics of their underlying Roster cards.
- [ ] Roster selection and Starship Trait surfaces retain existing layout, selection, quantities, and actions.
- [ ] A live artwork update does not change Roster membership, selection, Assignment state, Solution state, or Ship Filter projections.
- [ ] Workspace tests and visual baselines no longer construct or mock the retired factory or Icon Cache interfaces.
