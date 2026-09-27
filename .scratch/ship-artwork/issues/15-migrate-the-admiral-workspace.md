Status: resolved
Blocked by: 14

# 15: Migrate the Admiral workspace

## What to build

Use the application-owned Ship Artwork instance throughout the Admiral workspace so every Roster, selection, Assignment, Solution, and Starship Trait surface receives live artwork without changing domain behavior.

## Acceptance criteria

- [x] The workspace receives Ship Artwork through constructor ownership seams and does not import process-global application state.
- [x] Reusable Roster cards request specific presentation with fallback, while One-Time Ship cards retain generic presentation.
- [x] Assignment and Solution slots preserve the presentation semantics of their underlying Roster cards.
- [x] Roster selection and Starship Trait surfaces retain existing layout, selection, quantities, and actions.
- [x] A live artwork update does not change Roster membership, selection, Assignment state, Solution state, or Ship Filter projections.
- [x] Workspace tests and visual baselines no longer construct or mock the retired factory or Icon Cache interfaces.

## Comments

Resolved 2026-09-26. Ticket 14 had already threaded the application-owned Ship
Artwork through the Admiral workspace constructors and shared renderers, including
Roster cards, selection, Assignment slots, Solutions, and Starship Traits. This
ticket adds a scripted live-update regression through the Admiral workspace root
to verify stable specific artwork, generic One-Time presentation, and unchanged
Roster, selection, quantity, Assignment, Solution, and list-model state. The
visual-baseline guide now describes the real offline Ship Artwork fixture.

The focused workspace test and `.\gradlew.bat clean build` passed on JDK 25. All
nine generated headless visual captures matched the checked-in PNGs byte for byte.
