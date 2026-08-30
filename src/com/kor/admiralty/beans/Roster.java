/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.kor.admiralty.beans;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.kor.admiralty.io.GameData;

/**
 * Internal state machine for reusable cards and quantity-backed One-Time copies.
 * Admiral remains the only caller-facing mutation seam.
 * Mutations are caller-thread-confined; retained views provide stable snapshots without internal locking.
 */
final class Roster {

    private final GameData gameData;
    private final UUID cardIdentityOwner;
    private WorkingState state;
    private long revision;
    private RosterView view;

    /**
     * Groups the mutable structures that must be copied and committed as one Roster state.
     */
    private static final class WorkingState {

        private final Map<String, RosterCard> reusableCardsByShipName;
        private final Map<String, List<RosterCard>> oneTimeCardsByShipName;
        private final List<String> activeOrder;
        private final List<String> maintenanceOrder;

        /**
         * Creates an empty mutable state whose structures can later be swapped into the Roster together.
         */
        private WorkingState() {
            reusableCardsByShipName = new LinkedHashMap<String, RosterCard>();
            oneTimeCardsByShipName = new LinkedHashMap<String, List<RosterCard>>();
            activeOrder = new ArrayList<String>();
            maintenanceOrder = new ArrayList<String>();
        }

        /**
         * Creates an isolated transaction candidate from a committed state.
         * One-Time lists are copied individually so a rejected resize cannot mutate retained snapshots.
         *
         * @param source committed state to copy
         */
        private WorkingState(WorkingState source) {
            reusableCardsByShipName = new LinkedHashMap<String, RosterCard>(source.reusableCardsByShipName);
            oneTimeCardsByShipName = new LinkedHashMap<String, List<RosterCard>>();
            for (Map.Entry<String, List<RosterCard>> entry : source.oneTimeCardsByShipName.entrySet()) {
                oneTimeCardsByShipName.put(entry.getKey(), new ArrayList<RosterCard>(entry.getValue()));
            }
            activeOrder = new ArrayList<String>(source.activeOrder);
            maintenanceOrder = new ArrayList<String>(source.maintenanceOrder);
        }
    }

    /**
     * Holds canonical Ship facts and occurrence counts produced by one complete input validation pass.
     */
    private static final class CanonicalOneTimeQuantities {

        private final Map<String, Ship> shipsByName;
        private final Map<String, Integer> quantitiesByName;

        /**
         * Retains insertion order so compatibility persistence remains deterministic after aggregation.
         *
         * @param shipsByName canonical Ship facts keyed by canonical name
         * @param quantitiesByName requested occurrence counts keyed by the same names
         */
        private CanonicalOneTimeQuantities(
                Map<String, Ship> shipsByName,
                Map<String, Integer> quantitiesByName) {
            this.shipsByName = shipsByName;
            this.quantitiesByName = quantitiesByName;
        }
    }

    /**
     * Holds either one validated mixed-card Roster transaction or its expected rejection.
     */
    static final class DeploymentPlan {

        private final RosterView sourceView;
        private final List<RosterCard> cards;
        private final WorkingState updatedState;
        private final DeploymentRejection rejection;

        /**
         * Captures the immutable validation result and any working state prepared for commit.
         *
         * @param sourceView Roster view against which validation ran
         * @param cards exact current cards selected for a successful plan
         * @param updatedState complete prepared Roster state for a successful plan
         * @param rejection expected conflict for a rejected plan
         */
        private DeploymentPlan(
                RosterView sourceView,
                List<RosterCard> cards,
                WorkingState updatedState,
                DeploymentRejection rejection) {
            this.sourceView = sourceView;
            this.cards = cards;
            this.updatedState = updatedState;
            this.rejection = rejection;
        }

        /**
         * Returns the expected conflict found during validation.
         *
         * @return rejection, or {@code null} when this plan is ready to commit
         */
        DeploymentRejection getRejection() {
            return rejection;
        }

        /**
         * Returns the exact current cards selected by a successful plan.
         *
         * @return immutable selected-card list, or an empty list for a rejection
         */
        List<RosterCard> getCards() {
            return cards;
        }
    }

    /**
     * Creates an empty Roster bound to canonical GameData.
     *
     * @param gameData reference data used to canonicalize every mutation
     * @throws NullPointerException if {@code gameData} is null
     */
    private Roster(GameData gameData) {
        this.gameData = Objects.requireNonNull(gameData, "gameData");
        cardIdentityOwner = UUID.randomUUID();
        state = new WorkingState();
        revision = 0L;
        view = snapshot(revision, state);
    }

    /**
     * Restores canonical reusable cards and One-Time copies without treating startup state as a planning change.
     * Maintenance is applied last so it wins any conflicting historical Active entry.
     *
     * @param gameData canonical Ship reference data
     * @param activeNames persisted Active names; unknown names are ignored
     * @param maintenanceNames persisted Maintenance names; unknown names are ignored
     * @param oneTimeNames persisted repeated One-Time names; unknown names are ignored
     * @return a restored Roster whose initial revision is zero
     * @throws NullPointerException if an argument is null
     */
    static Roster restore(
            GameData gameData,
            Collection<String> activeNames,
            Collection<String> maintenanceNames,
            Collection<String> oneTimeNames) {
        Roster roster = new Roster(gameData);
        roster.restoreNames(activeNames, RosterState.ACTIVE);
        roster.restoreNames(maintenanceNames, RosterState.MAINTENANCE);
        roster.restoreOneTimeNames(oneTimeNames);
        roster.view = roster.snapshot(roster.revision, roster.state);
        return roster;
    }

    /**
     * Returns the current immutable Roster snapshot.
     *
     * @return the exact view retained for the current revision
     */
    RosterView view() {
        return view;
    }

    /**
     * Returns canonical names for a present state in compatibility persistence order.
     *
     * @param rosterState Active or Maintenance
     * @return an unmodifiable copy of canonical Ship names
     * @throws IllegalArgumentException if {@code rosterState} is Absent
     * @throws NullPointerException if {@code rosterState} is null
     */
    List<String> names(RosterState rosterState) {
        return Collections.unmodifiableList(new ArrayList<String>(orderFor(rosterState)));
    }

    /**
     * Expands current One-Time quantities into canonical names for the historical repeated-element format.
     *
     * @return an unmodifiable expanded list in canonical compatibility order
     */
    List<String> oneTimeNames() {
        List<String> names = new ArrayList<String>();
        for (Map.Entry<String, List<RosterCard>> entry : state.oneTimeCardsByShipName.entrySet()) {
            for (int copy = 0; copy < entry.getValue().size(); copy++) {
                names.add(entry.getKey());
            }
        }
        return Collections.unmodifiableList(names);
    }

    /**
     * Rejects card identities that were never issued by this Roster as caller misuse.
     * Each identity retains its opaque Roster scope after removal, distinguishing it from a foreign card.
     *
     * @param cards selected cards to verify
     * @throws IllegalArgumentException if any card belongs to another Admiral
     * @throws NullPointerException if {@code cards} or one of its elements is null
     */
    void requireOwnedCardIdentities(Collection<RosterCard> cards) {
        Objects.requireNonNull(cards, "cards");
        for (RosterCard card : cards) {
            Objects.requireNonNull(card, "cards contains null");
            if (!card.getId().isOwnedBy(cardIdentityOwner)) {
                throw new IllegalArgumentException("Roster card does not belong to this Admiral");
            }
        }
    }

    /**
     * Validates a complete deployment batch and prepares its mixed-card working state without committing it.
     *
     * @param cards locally owned cards selected by one current-revision Solution
     * @return a ready transaction plan or its first structured expected rejection
     * @throws IllegalArgumentException if the selection is empty or a card belongs to another Admiral
     * @throws ArithmeticException if the requested occurrence count exceeds the integer range
     * @throws NullPointerException if {@code cards} or one of its elements is null
     */
    DeploymentPlan prepareDeployment(Collection<RosterCard> cards) {
        requireOwnedCardIdentities(cards);
        if (cards.isEmpty()) {
            throw new IllegalArgumentException("A deployment must select at least one Roster card");
        }
        Map<RosterCardId, RosterCard> currentCardsById = currentCardsById();
        Set<RosterCardId> selectedIds = new LinkedHashSet<RosterCardId>();
        Map<String, Integer> requestedOneTimeQuantities = new LinkedHashMap<String, Integer>();
        Map<String, Ship> requestedOneTimeShips = new LinkedHashMap<String, Ship>();
        for (RosterCard card : cards) {
            if (!selectedIds.add(card.getId())) {
                return rejectedDeployment(DeploymentRejection.duplicate(card));
            }
            if (card.getKind() == RosterCardKind.ONE_TIME) {
                String shipName = card.getShip().getName();
                requestedOneTimeShips.putIfAbsent(shipName, card.getShip());
                requestedOneTimeQuantities.put(
                        shipName,
                        Math.addExact(requestedOneTimeQuantities.getOrDefault(shipName, 0), 1));
            }
        }

        for (Map.Entry<String, Integer> entry : requestedOneTimeQuantities.entrySet()) {
            List<RosterCard> availableCards = state.oneTimeCardsByShipName.get(entry.getKey());
            int availableQuantity = availableCards == null ? 0 : availableCards.size();
            if (entry.getValue() > availableQuantity) {
                return rejectedDeployment(DeploymentRejection.insufficientOneTimeQuantity(
                        requestedOneTimeShips.get(entry.getKey()),
                        entry.getValue(),
                        availableQuantity));
            }
        }

        List<RosterCard> selectedCards = new ArrayList<RosterCard>(cards.size());
        for (RosterCard card : cards) {
            RosterCard currentCard = currentCardsById.get(card.getId());
            if (currentCard == null
                    || currentCard.getKind() != card.getKind()
                    || currentCard.getShip() != card.getShip()
                    || (currentCard.getKind() == RosterCardKind.REUSABLE
                    && currentCard.getState() != RosterState.ACTIVE)) {
                return rejectedDeployment(DeploymentRejection.unavailable(card));
            }
            selectedCards.add(currentCard);
        }

        WorkingState updated = new WorkingState(state);
        for (RosterCard card : selectedCards) {
            String shipName = card.getShip().getName();
            if (card.getKind() == RosterCardKind.REUSABLE) {
                updated.activeOrder.remove(shipName);
                updated.maintenanceOrder.add(shipName);
                updated.reusableCardsByShipName.put(
                        shipName,
                        new RosterCard(card.getId(), card.getShip(), RosterState.MAINTENANCE));
            } else {
                List<RosterCard> copies = updated.oneTimeCardsByShipName.get(shipName);
                copies.removeIf(copy -> copy.getId().equals(card.getId()));
                if (copies.isEmpty()) {
                    updated.oneTimeCardsByShipName.remove(shipName);
                }
            }
        }
        return new DeploymentPlan(
                view,
                Collections.unmodifiableList(selectedCards),
                updated,
                null);
    }

    /**
     * Commits one previously validated deployment plan against the unchanged source Roster view.
     *
     * @param deploymentPlan ready plan returned by {@link #prepareDeployment(Collection)}
     * @return the single committed before/after Roster change
     * @throws IllegalArgumentException if the plan contains an expected rejection
     * @throws IllegalStateException if another Roster mutation invalidated the plan before commit
     * @throws ArithmeticException if the Roster revision counter overflows
     * @throws NullPointerException if {@code deploymentPlan} is null
     */
    RosterChange commitDeployment(DeploymentPlan deploymentPlan) {
        Objects.requireNonNull(deploymentPlan, "deploymentPlan");
        if (deploymentPlan.rejection != null) {
            throw new IllegalArgumentException("A rejected deployment plan cannot be committed");
        }
        if (deploymentPlan.sourceView != view) {
            throw new IllegalStateException("Roster changed after deployment validation");
        }
        return commit(deploymentPlan.updatedState);
    }

    /**
     * Wraps one expected conflict without allocating a transaction working state.
     *
     * @param rejection expected conflict found during validation
     * @return rejected deployment plan tied to the current Roster view
     * @throws NullPointerException if {@code rejection} is null
     */
    private DeploymentPlan rejectedDeployment(DeploymentRejection rejection) {
        return new DeploymentPlan(
                view,
                Collections.emptyList(),
                null,
                Objects.requireNonNull(rejection, "rejection"));
    }

    /**
     * Indexes every current reusable and One-Time card by its opaque runtime identity.
     *
     * @return current cards keyed by identity
     */
    private Map<RosterCardId, RosterCard> currentCardsById() {
        Map<RosterCardId, RosterCard> cardsById = new LinkedHashMap<RosterCardId, RosterCard>();
        for (RosterCard card : state.reusableCardsByShipName.values()) {
            cardsById.put(card.getId(), card);
        }
        for (List<RosterCard> copies : state.oneTimeCardsByShipName.values()) {
            for (RosterCard card : copies) {
                cardsById.put(card.getId(), card);
            }
        }
        return cardsById;
    }

    /**
     * Adjusts the available quantity for one canonical One-Time Ship as a single committed change.
     * Existing copy identities are retained when possible; newly added copies receive fresh runtime identities.
     *
     * @param ship Ship to canonicalize through this Roster's GameData
     * @param adjustment signed quantity change
     * @return the committed before/after change, or null when {@code adjustment} is zero
     * @throws IllegalArgumentException if the Ship is unknown or the resulting quantity would be negative
     * @throws ArithmeticException if the resulting quantity exceeds the integer range
     * @throws NullPointerException if {@code ship} is null
     */
    RosterChange adjustOneTimeShipQuantity(Ship ship, int adjustment) {
        Objects.requireNonNull(ship, "ship");
        return adjustOneTimeShipQuantities(List.of(ship), adjustment);
    }

    /**
     * Applies one signed adjustment per supplied Ship occurrence after validating the whole batch.
     * No working state is copied until every resulting quantity is known to be valid.
     *
     * @param ships repeated Ships whose quantities should change
     * @param adjustmentPerOccurrence signed adjustment applied for each collection occurrence
     * @return the committed before/after change, or null for an empty collection or zero adjustment
     * @throws IllegalArgumentException if a Ship is unknown or any resulting quantity would be negative
     * @throws ArithmeticException if an adjustment or resulting quantity exceeds the integer range
     * @throws NullPointerException if {@code ships} or one of its elements is null
     */
    RosterChange adjustOneTimeShipQuantities(Collection<Ship> ships, int adjustmentPerOccurrence) {
        CanonicalOneTimeQuantities requested = canonicalOneTimeQuantities(ships);
        if (requested.quantitiesByName.isEmpty() || adjustmentPerOccurrence == 0) {
            return null;
        }

        Map<String, Integer> updatedQuantities = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> entry : requested.quantitiesByName.entrySet()) {
            List<RosterCard> currentCards = state.oneTimeCardsByShipName.get(entry.getKey());
            int currentQuantity = currentCards == null ? 0 : currentCards.size();
            int adjustment = Math.multiplyExact(entry.getValue(), adjustmentPerOccurrence);
            int updatedQuantity = Math.addExact(currentQuantity, adjustment);
            if (updatedQuantity < 0) {
                throw new IllegalArgumentException("One-Time Ship quantity cannot be negative: " + entry.getKey());
            }
            updatedQuantities.put(entry.getKey(), updatedQuantity);
        }

        WorkingState updated = new WorkingState(state);
        for (Map.Entry<String, Integer> entry : updatedQuantities.entrySet()) {
            resizeOneTimeCards(
                    updated.oneTimeCardsByShipName,
                    entry.getKey(),
                    requested.shipsByName.get(entry.getKey()),
                    entry.getValue());
        }
        return commit(updated);
    }

    /**
     * Replaces all One-Time quantities from a repeated Ship collection while retaining surviving copy identities.
     * Input ordering is compatibility-only, so equal canonical quantities are a no-op.
     *
     * @param ships repeated Ship-shaped values to canonicalize
     * @return the committed before/after change, or null when every canonical quantity is unchanged
     * @throws IllegalArgumentException if a Ship is unknown
     * @throws NullPointerException if {@code ships} or one of its elements is null
     */
    RosterChange replaceOneTimeShips(Collection<Ship> ships) {
        CanonicalOneTimeQuantities requested = canonicalOneTimeQuantities(ships);

        if (sameOneTimeQuantities(requested.quantitiesByName)) {
            return null;
        }

        WorkingState updated = new WorkingState(state);
        Map<String, List<RosterCard>> replacement = new LinkedHashMap<String, List<RosterCard>>();
        for (Map.Entry<String, Integer> entry : requested.quantitiesByName.entrySet()) {
            List<RosterCard> cards = updated.oneTimeCardsByShipName.get(entry.getKey());
            if (cards != null) {
                replacement.put(entry.getKey(), cards);
            }
            resizeOneTimeCards(
                    replacement,
                    entry.getKey(),
                    requested.shipsByName.get(entry.getKey()),
                    entry.getValue());
        }
        updated.oneTimeCardsByShipName.clear();
        updated.oneTimeCardsByShipName.putAll(replacement);
        return commit(updated);
    }

    /**
     * Canonicalizes repeated Ship inputs and counts every occurrence before a mutation starts.
     *
     * @param ships repeated Ship-shaped values to canonicalize
     * @return canonical facts and non-negative counts in first-type-occurrence order
     * @throws IllegalArgumentException if a Ship is unknown
     * @throws ArithmeticException if one type's occurrence count exceeds the integer range
     * @throws NullPointerException if {@code ships} or one of its elements is null
     */
    private CanonicalOneTimeQuantities canonicalOneTimeQuantities(Collection<Ship> ships) {
        Objects.requireNonNull(ships, "ships");
        Map<String, Ship> canonicalShips = new LinkedHashMap<String, Ship>();
        Map<String, Integer> quantities = new LinkedHashMap<String, Integer>();
        for (Ship ship : ships) {
            Ship canonicalShip = canonicalShip(ship);
            String canonicalName = canonicalShip.getName();
            canonicalShips.putIfAbsent(canonicalName, canonicalShip);
            quantities.put(canonicalName, Math.addExact(quantities.getOrDefault(canonicalName, 0), 1));
        }
        return new CanonicalOneTimeQuantities(canonicalShips, quantities);
    }

    /**
     * Resolves one Ship-shaped value through this Roster's canonical GameData.
     *
     * @param ship Ship-shaped value to resolve
     * @return canonical Ship facts
     * @throws IllegalArgumentException if the Ship is unknown
     * @throws NullPointerException if {@code ship} is null
     */
    private Ship canonicalShip(Ship ship) {
        Objects.requireNonNull(ship, "ships contains null");
        Ship canonicalShip = gameData.ship(ship.getName());
        if (canonicalShip == null) {
            throw new IllegalArgumentException("Ship is not present in this Admiral's GameData: " + ship.getName());
        }
        return canonicalShip;
    }

    /**
     * Resizes one identity-bearing copy list while retaining the earliest surviving identities.
     * Removing from the end keeps any previously selected earlier copies stable for later migration phases.
     *
     * @param cardsByShipName destination copy lists keyed by canonical Ship name
     * @param shipName canonical Ship name to resize
     * @param ship canonical Ship facts for newly created copies
     * @param quantity requested non-negative quantity
     */
    private void resizeOneTimeCards(
            Map<String, List<RosterCard>> cardsByShipName,
            String shipName,
            Ship ship,
            int quantity) {
        List<RosterCard> cards = cardsByShipName.computeIfAbsent(
                shipName,
                ignored -> new ArrayList<RosterCard>());
        while (cards.size() < quantity) {
            cards.add(new RosterCard(
                    createCardId(),
                    ship,
                    RosterCardKind.ONE_TIME,
                    RosterState.ONE_TIME));
        }
        while (cards.size() > quantity) {
            cards.remove(cards.size() - 1);
        }
        if (cards.isEmpty()) {
            cardsByShipName.remove(shipName);
        }
    }

    /**
     * Compares requested canonical quantities without treating type insertion order as planning state.
     *
     * @param requestedQuantities desired quantities keyed by canonical Ship name
     * @return {@code true} when the current One-Time quantities are identical
     */
    private boolean sameOneTimeQuantities(Map<String, Integer> requestedQuantities) {
        if (state.oneTimeCardsByShipName.size() != requestedQuantities.size()) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : requestedQuantities.entrySet()) {
            List<RosterCard> currentCards = state.oneTimeCardsByShipName.get(entry.getKey());
            if (currentCards == null || currentCards.size() != entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Adds canonical reusable Ships to one state, atomically moving cards already in the other state.
     *
     * @param ships Ships to canonicalize through this Roster's GameData
     * @param destination Active or Maintenance
     * @return the committed before/after change, or null when every Ship is already at the destination
     * @throws IllegalArgumentException if a Ship is unknown or the destination is Absent
     * @throws NullPointerException if an argument or collection element is null
     */
    RosterChange addReusableShips(Collection<Ship> ships, RosterState destination) {
        requirePresentState(destination);
        Map<String, Ship> canonicalShips = canonicalShips(ships);

        WorkingState updated = new WorkingState(state);
        boolean changed = false;
        for (Ship ship : canonicalShips.values()) {
            String name = ship.getName();
            RosterCard current = updated.reusableCardsByShipName.get(name);
            if (current != null && current.getState() == destination) {
                continue;
            }

            if (current != null) {
                orderFor(current.getState(), updated).remove(name);
                updated.reusableCardsByShipName.put(name, new RosterCard(current.getId(), ship, destination));
            } else {
                updated.reusableCardsByShipName.put(name, new RosterCard(createCardId(), ship, destination));
            }
            orderFor(destination, updated).add(name);
            changed = true;
        }
        if (!changed) {
            return null;
        }

        return commit(updated);
    }

    /**
     * Moves identity-bearing reusable cards to one destination as a single committed change.
     *
     * @param cards current cards selected from this Roster's retained or prior views
     * @param destination Active or Maintenance
     * @return the committed before/after change, or null when every card is already at the destination
     * @throws IllegalArgumentException if a card is foreign or removed, or the destination is Absent
     * @throws NullPointerException if an argument or collection element is null
     */
    RosterChange moveReusableCards(Collection<RosterCard> cards, RosterState destination) {
        requirePresentState(destination);
        Map<RosterCardId, String> selectedNames = currentNamesFor(cards);
        WorkingState updated = new WorkingState(state);
        boolean changed = false;
        for (String name : selectedNames.values()) {
            RosterCard current = updated.reusableCardsByShipName.get(name);
            if (current.getState() == destination) {
                continue;
            }
            orderFor(current.getState(), updated).remove(name);
            orderFor(destination, updated).add(name);
            updated.reusableCardsByShipName.put(name, new RosterCard(current.getId(), current.getShip(), destination));
            changed = true;
        }
        if (!changed) {
            return null;
        }

        return commit(updated);
    }

    /**
     * Removes identity-bearing reusable cards as a single committed change.
     *
     * @param cards current cards selected from this Roster's retained or prior views
     * @return the committed before/after change, or null when {@code cards} is empty
     * @throws IllegalArgumentException if a card is foreign or removed
     * @throws NullPointerException if {@code cards} or one of its elements is null
     */
    RosterChange removeReusableCards(Collection<RosterCard> cards) {
        Map<RosterCardId, String> selectedNames = currentNamesFor(cards);
        if (selectedNames.isEmpty()) {
            return null;
        }

        WorkingState updated = new WorkingState(state);
        for (String name : selectedNames.values()) {
            RosterCard removed = updated.reusableCardsByShipName.remove(name);
            orderFor(removed.getState(), updated).remove(name);
        }

        return commit(updated);
    }

    /**
     * Replaces one reusable state for persistence and legacy callers while preserving the other state's cards.
     * Input order alone is not planning-relevant and therefore does not create a new revision.
     *
     * @param ships complete replacement membership for the destination
     * @param destination Active or Maintenance
     * @return the committed before/after change, or null when membership and states are unchanged
     * @throws IllegalArgumentException if a Ship is unknown or the destination is Absent
     * @throws NullPointerException if an argument or collection element is null
     */
    RosterChange replaceReusableShips(Collection<Ship> ships, RosterState destination) {
        requirePresentState(destination);
        Map<String, Ship> canonicalShips = canonicalShips(ships);
        List<String> replacementOrder = new ArrayList<String>(canonicalShips.keySet());

        WorkingState updated = new WorkingState(state);
        List<String> destinationOrder = orderFor(destination, updated);
        boolean changed = destinationOrder.size() != canonicalShips.size()
                || !canonicalShips.keySet().containsAll(destinationOrder);

        for (String currentName : new ArrayList<String>(destinationOrder)) {
            if (!canonicalShips.containsKey(currentName)) {
                updated.reusableCardsByShipName.remove(currentName);
                changed = true;
            }
        }
        for (Ship ship : canonicalShips.values()) {
            String name = ship.getName();
            RosterCard current = updated.reusableCardsByShipName.get(name);
            if (current == null) {
                updated.reusableCardsByShipName.put(name, new RosterCard(createCardId(), ship, destination));
                changed = true;
            } else if (current.getState() != destination) {
                orderFor(current.getState(), updated).remove(name);
                updated.reusableCardsByShipName.put(name, new RosterCard(current.getId(), ship, destination));
                changed = true;
            }
        }
        if (!changed) {
            return null;
        }
        destinationOrder.clear();
        destinationOrder.addAll(replacementOrder);

        return commit(updated);
    }

    /**
     * Restores known canonical names directly into the startup state without advancing its revision.
     *
     * @param names persisted names to restore; unknown names are ignored
     * @param destination Active or Maintenance
     * @throws NullPointerException if {@code names} is null
     */
    private void restoreNames(Collection<String> names, RosterState destination) {
        Objects.requireNonNull(names, "names");
        for (String name : names) {
            Ship canonicalShip = gameData.ship(name);
            if (canonicalShip == null) {
                continue;
            }
            String canonicalName = canonicalShip.getName();
            RosterCard current = state.reusableCardsByShipName.get(canonicalName);
            if (current != null && current.getState() == destination) {
                continue;
            }
            if (current != null) {
                orderFor(current.getState()).remove(canonicalName);
                state.reusableCardsByShipName.put(
                        canonicalName,
                        new RosterCard(current.getId(), canonicalShip, destination));
            } else {
                state.reusableCardsByShipName.put(
                        canonicalName,
                        new RosterCard(createCardId(), canonicalShip, destination));
            }
            orderFor(destination).add(canonicalName);
        }
    }

    /**
     * Restores repeated historical One-Time elements as independently identified copies at revision zero.
     *
     * @param names persisted One-Time names to canonicalize; unknown names are ignored
     * @throws NullPointerException if {@code names} or one of its elements is null
     */
    private void restoreOneTimeNames(Collection<String> names) {
        Objects.requireNonNull(names, "oneTimeNames");
        for (String name : names) {
            Objects.requireNonNull(name, "oneTimeNames contains null");
            Ship canonicalShip = gameData.ship(name);
            if (canonicalShip == null) {
                continue;
            }
            state.oneTimeCardsByShipName
                    .computeIfAbsent(canonicalShip.getName(), ignored -> new ArrayList<RosterCard>())
                    .add(new RosterCard(
                            createCardId(),
                            canonicalShip,
                            RosterCardKind.ONE_TIME,
                            RosterState.ONE_TIME));
        }
    }

    /**
     * Resolves and deduplicates Ships through this Roster's reference data before any mutation begins.
     *
     * @param ships Ship-shaped inputs to resolve
     * @return canonical Ships keyed by canonical name in first-occurrence order
     * @throws IllegalArgumentException if a Ship is absent from the reference data
     * @throws NullPointerException if {@code ships} or one of its elements is null
     */
    private Map<String, Ship> canonicalShips(Collection<Ship> ships) {
        Objects.requireNonNull(ships, "ships");
        Map<String, Ship> canonicalShips = new LinkedHashMap<String, Ship>();
        for (Ship ship : ships) {
            Ship canonicalShip = canonicalShip(ship);
            canonicalShips.putIfAbsent(canonicalShip.getName(), canonicalShip);
        }
        return canonicalShips;
    }

    /**
     * Resolves selected opaque identities against the current Roster before any mutation begins.
     *
     * @param cards cards from this Roster's current or retained views
     * @return selected current names keyed by identity, with duplicate inputs removed
     * @throws IllegalArgumentException if a card is foreign or has been removed
     * @throws NullPointerException if {@code cards} or one of its elements is null
     */
    private Map<RosterCardId, String> currentNamesFor(Collection<RosterCard> cards) {
        Objects.requireNonNull(cards, "cards");
        Map<RosterCardId, String> currentNames = new LinkedHashMap<RosterCardId, String>();
        for (Map.Entry<String, RosterCard> entry : state.reusableCardsByShipName.entrySet()) {
            currentNames.put(entry.getValue().getId(), entry.getKey());
        }

        Map<RosterCardId, String> selectedNames = new LinkedHashMap<RosterCardId, String>();
        for (RosterCard card : cards) {
            Objects.requireNonNull(card, "cards contains null");
            String currentName = currentNames.get(card.getId());
            if (currentName == null) {
                throw new IllegalArgumentException("Roster card does not belong to this Admiral");
            }
            selectedNames.putIfAbsent(card.getId(), currentName);
        }
        return selectedNames;
    }

    /**
     * Creates one runtime identity scoped to this Roster so removed local cards remain distinguishable from foreign cards.
     *
     * @return a fresh identity owned by this Roster
     */
    private RosterCardId createCardId() {
        return RosterCardId.create(cardIdentityOwner);
    }

    /**
     * Builds the complete next view before swapping fields, then commits every mutable reference together.
     *
     * @param updated fully prepared mutable state owned by this Roster after the call
     * @return one immutable before/after change for listener delivery
     * @throws ArithmeticException if the revision counter overflows
     */
    private RosterChange commit(WorkingState updated) {
        long updatedRevision = Math.incrementExact(revision);
        RosterView updatedView = snapshot(updatedRevision, updated);
        RosterView before = view;
        state = updated;
        revision = updatedRevision;
        view = updatedView;
        return new RosterChange(before, updatedView);
    }

    /**
     * Projects a complete immutable public view from one coherent working state.
     *
     * @param snapshotRevision revision represented by the state
     * @param snapshotState source state that will no longer be mutated after commit
     * @return a naturally ordered immutable Roster view
     */
    private RosterView snapshot(long snapshotRevision, WorkingState snapshotState) {
        List<RosterCard> activeCards = cardsInNaturalOrder(
                snapshotState.activeOrder,
                snapshotState.reusableCardsByShipName);
        List<RosterCard> maintenanceCards = cardsInNaturalOrder(
                snapshotState.maintenanceOrder,
                snapshotState.reusableCardsByShipName);
        List<RosterCard> oneTimeCards = oneTimeCardsInNaturalOrder(snapshotState.oneTimeCardsByShipName);
        return new RosterView(snapshotRevision, activeCards, maintenanceCards, oneTimeCards);
    }

    /**
     * Projects cards for one state into stable natural Ship order.
     *
     * @param names canonical names in compatibility persistence order
     * @param snapshotCards cards keyed by canonical name
     * @return cards sorted by their canonical Ship ordering
     */
    private List<RosterCard> cardsInNaturalOrder(
            List<String> names,
            Map<String, RosterCard> snapshotCards) {
        List<RosterCard> cards = new ArrayList<RosterCard>();
        for (String name : names) {
            cards.add(snapshotCards.get(name));
        }
        cards.sort((left, right) -> left.getShip().compareTo(right.getShip()));
        return cards;
    }

    /**
     * Projects every One-Time copy into natural Ship order without collapsing equal canonical facts.
     * Java's stable sort retains copy identity order within one Ship type.
     *
     * @param cardsByShipName One-Time copies grouped by canonical Ship name
     * @return naturally ordered identity-bearing copies
     */
    private List<RosterCard> oneTimeCardsInNaturalOrder(Map<String, List<RosterCard>> cardsByShipName) {
        List<RosterCard> cards = new ArrayList<RosterCard>();
        for (List<RosterCard> shipCards : cardsByShipName.values()) {
            cards.addAll(shipCards);
        }
        cards.sort((left, right) -> left.getShip().compareTo(right.getShip()));
        return cards;
    }

    /**
     * Returns the mutable compatibility order for one present state.
     *
     * @param rosterState Active or Maintenance
     * @return the order list owned by the current working state
     * @throws IllegalArgumentException if {@code rosterState} is Absent
     * @throws NullPointerException if {@code rosterState} is null
     */
    private List<String> orderFor(RosterState rosterState) {
        return orderFor(rosterState, state);
    }

    /**
     * Returns the mutable compatibility order for one present state in a supplied working copy.
     *
     * @param rosterState Active or Maintenance
     * @param workingState state whose order list is required
     * @return the selected mutable order list
     * @throws IllegalArgumentException if {@code rosterState} is Absent
     * @throws NullPointerException if {@code rosterState} or {@code workingState} is null
     */
    private static List<String> orderFor(
            RosterState rosterState,
            WorkingState workingState) {
        Objects.requireNonNull(rosterState, "state");
        Objects.requireNonNull(workingState, "workingState");
        if (rosterState == RosterState.ACTIVE) {
            return workingState.activeOrder;
        }
        if (rosterState == RosterState.MAINTENANCE) {
            return workingState.maintenanceOrder;
        }
        throw new IllegalArgumentException("Only Active and Maintenance cards have a reusable Roster order");
    }

    /**
     * Rejects non-reusable destinations because absence and One-Time quantities use dedicated operations.
     *
     * @param rosterState requested mutation destination
     * @throws IllegalArgumentException if {@code rosterState} is Absent or One-Time
     * @throws NullPointerException if {@code rosterState} is null
     */
    private static void requirePresentState(RosterState rosterState) {
        Objects.requireNonNull(rosterState, "state");
        if (rosterState != RosterState.ACTIVE && rosterState != RosterState.MAINTENANCE) {
            throw new IllegalArgumentException("Use the dedicated operation for Absent or One-Time cards");
        }
    }
}
