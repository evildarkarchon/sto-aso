# STO Admiralty System Optimizer

A desktop tool that helps a Star Trek Online player pick which of their ships to send on Admiralty assignments. Reference data about ships, assignments and events is shipped as CSV files and refreshed from the project's GitHub repository; each player's own ships are stored locally per Admiral.

## Language

### Reference data

**GameData**:
The read-only reference data the app ships with and refreshes from GitHub: ships, renamed ships, starship traits, assignments and events. Loaded once at startup; per-player state does not live here.
_Avoid_: Datastore, ship database (too narrow — it also holds assignments and events), catalogue

**GameData Refresh**:
A background attempt to replace the shipped GameData as one coherent set. A successful refresh becomes active on the next application launch. When a refresh reports failure while the application remains running, it does not expose a partial set; interruption by process or machine termination is outside this guarantee.
_Avoid_: live reload (the refreshed data is not applied to the running application), data update (too generic)

**Ship**:
A ship type as defined by the game, with its faction, tier, rarity, role, Eng/Tac/Sci stats and Special Ability. One entry per ship name in GameData; it carries no per-Admiral Roster or usage state.
_Avoid_: vessel, starship (reserved for Starship Trait)

**Ship Filter**:
The visibility and ordering criteria applied when presenting Ships from GameData, a Roster or usage history. It uses canonical Ship facts and never changes GameData or a Roster.
_Avoid_: Ship Selection (not every filtered view selects), Ship Browser (presentation-specific)

**One-Time Ship**:
A Ship card that can be deployed exactly once and is then consumed, as opposed to a ship the player owns permanently.
_Avoid_: consumable, temporary ship

**Renamed Ship**:
A mapping from a ship's old name to its current name, used to migrate an Admiral's saved ship names when the game renames a ship.
_Avoid_: alias

**Starship Trait**:
The player trait unlocked by mastering a ship; shown alongside the ship so a player can see which traits they own.
_Avoid_: trait (ambiguous with ability), mastery

**Special Ability**:
The bonus text on an Admiralty ship card, parsed into a rule (when it fires and which slots it targets) and a reward (what it changes on the solution).
_Avoid_: bonus, rule (only half of it), reward (only half of it)

### Assignments

**Assignment**:
An Admiralty mission with required Eng/Tac/Sci totals, a target critical rate and up to three ship slots.
_Avoid_: mission, task

**Event**:
A time-limited modifier to an Assignment's requirements or critical rate.
_Avoid_: modifier

**Solution**:
A scored choice of up to three ships for one Assignment. A composite solution covers up to three Assignments with no ship used twice.
_Avoid_: result, plan

### Player

**Roster**:
The Ship cards belonging to one Admiral: each reusable Ship is either Active or in Maintenance, while One-Time Ships are held as quantities. Historical usage is not part of the Roster.
_Avoid_: Fleet, inventory, owned Ships

**Admiral**:
One of the player's characters. Has a Roster, per-Ship usage counts and current Assignments.
_Avoid_: character, player, profile

**Icon Cache**:
The locally persisted set of composed ship icons (icon + faction background + role and rarity frames), filled from bundled images and downloads from GitHub.
_Avoid_: image cache
