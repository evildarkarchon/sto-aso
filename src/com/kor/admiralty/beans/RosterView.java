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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, internally consistent view of one Admiral's complete Roster at one revision.
 * Canonical GameData Ship references are retained as stable reference facts rather than copied.
 */
public final class RosterView {

    private final long revision;
    private final List<RosterCard> activeCards;
    private final List<RosterCard> maintenanceCards;
    private final List<RosterCard> activeCardsInRosterOrder;
    private final List<RosterCard> maintenanceCardsInRosterOrder;
    private final List<RosterCard> reusableCards;
    private final List<RosterCard> oneTimeCards;
    private final List<RosterCard> oneTimeCardsInRosterOrder;
    private final List<RosterCard> cards;
    private final List<RosterCard> reusableFirstDeployableCards;
    private final List<RosterCard> oneTimeFirstDeployableCards;
    private final Map<String, RosterState> reusableStatesByShipName;
    private final Map<String, Integer> oneTimeQuantitiesByShipName;

    /**
     * Builds every immutable projection from lists captured during one Roster commit.
     * Group concatenation deliberately preserves reusable-versus-One-Time priority instead of globally sorting.
     *
     * @param revision revision shared by every projection
     * @param activeCards naturally ordered Active reusable cards
     * @param maintenanceCards naturally ordered Maintenance reusable cards
     * @param oneTimeCards naturally ordered identity-bearing One-Time copies
     * @param activeCardsInRosterOrder Active cards in stable insertion order
     * @param maintenanceCardsInRosterOrder Maintenance cards in stable insertion order
     * @param oneTimeCardsInRosterOrder One-Time copies grouped by stable Ship-type insertion order
     * @throws NullPointerException if a list or one of its cards is null
     */
    RosterView(
            long revision,
            List<RosterCard> activeCards,
            List<RosterCard> maintenanceCards,
            List<RosterCard> oneTimeCards,
            List<RosterCard> activeCardsInRosterOrder,
            List<RosterCard> maintenanceCardsInRosterOrder,
            List<RosterCard> oneTimeCardsInRosterOrder) {
        this.revision = revision;
        this.activeCards = immutableCopy(activeCards);
        this.maintenanceCards = immutableCopy(maintenanceCards);
        this.oneTimeCards = immutableCopy(oneTimeCards);
        this.activeCardsInRosterOrder = immutableCopy(activeCardsInRosterOrder);
        this.maintenanceCardsInRosterOrder = immutableCopy(maintenanceCardsInRosterOrder);
        this.oneTimeCardsInRosterOrder = immutableCopy(oneTimeCardsInRosterOrder);
        List<RosterCard> reusableCardSnapshot = new ArrayList<RosterCard>(
                activeCards.size() + maintenanceCards.size());
        reusableCardSnapshot.addAll(activeCards);
        reusableCardSnapshot.addAll(maintenanceCards);
        reusableCards = Collections.unmodifiableList(reusableCardSnapshot);

        List<RosterCard> completeCards = new ArrayList<RosterCard>(
                reusableCardSnapshot.size() + oneTimeCards.size());
        completeCards.addAll(reusableCardSnapshot);
        completeCards.addAll(oneTimeCards);
        cards = Collections.unmodifiableList(completeCards);

        List<RosterCard> reusableFirst = new ArrayList<RosterCard>(activeCards.size() + oneTimeCards.size());
        reusableFirst.addAll(activeCards);
        reusableFirst.addAll(oneTimeCards);
        reusableFirstDeployableCards = Collections.unmodifiableList(reusableFirst);

        List<RosterCard> oneTimeFirst = new ArrayList<RosterCard>(activeCards.size() + oneTimeCards.size());
        oneTimeFirst.addAll(oneTimeCards);
        oneTimeFirst.addAll(activeCards);
        oneTimeFirstDeployableCards = Collections.unmodifiableList(oneTimeFirst);

        Map<String, RosterState> reusableStates = new HashMap<String, RosterState>();
        for (RosterCard card : reusableCardSnapshot) {
            reusableStates.put(card.getShip().getName(), card.getState());
        }
        reusableStatesByShipName = Collections.unmodifiableMap(reusableStates);

        Map<String, Integer> quantities = new HashMap<String, Integer>();
        for (RosterCard card : oneTimeCards) {
            String shipName = card.getShip().getName();
            quantities.put(shipName, quantities.getOrDefault(shipName, 0) + 1);
        }
        oneTimeQuantitiesByShipName = Collections.unmodifiableMap(quantities);
    }

    /**
     * Returns the planning revision represented by every collection in this view.
     *
     * @return non-negative complete Roster revision
     */
    public long getRevision() {
        return revision;
    }

    /**
     * Returns naturally ordered reusable cards that are currently Active.
     *
     * @return unmodifiable Active-card list
     */
    public List<RosterCard> getActiveCards() {
        return activeCards;
    }

    /**
     * Returns naturally ordered reusable cards that are currently in Maintenance.
     *
     * @return unmodifiable Maintenance-card list
     */
    public List<RosterCard> getMaintenanceCards() {
        return maintenanceCards;
    }

    /**
     * Returns all present reusable cards, grouped as Active then Maintenance.
     *
     * @return unmodifiable reusable-card list
     */
    public List<RosterCard> getReusableCards() {
        return reusableCards;
    }

    /**
     * Returns all independently selectable One-Time copies in natural Ship order.
     *
     * @return unmodifiable One-Time-card list
     */
    public List<RosterCard> getOneTimeCards() {
        return oneTimeCards;
    }

    /**
     * Returns Active cards in the Roster's stable insertion order.
     * This view lets persistence preserve historical repeated-element ordering without exposing mutable name lists.
     *
     * @return unmodifiable Active-card list in stable Roster order
     */
    public List<RosterCard> getActiveCardsInRosterOrder() {
        return activeCardsInRosterOrder;
    }

    /**
     * Returns Maintenance cards in the Roster's stable insertion order.
     *
     * @return unmodifiable Maintenance-card list in stable Roster order
     */
    public List<RosterCard> getMaintenanceCardsInRosterOrder() {
        return maintenanceCardsInRosterOrder;
    }

    /**
     * Returns One-Time copies grouped by the stable insertion order of their canonical Ship type.
     *
     * @return unmodifiable One-Time-card list in stable Roster order
     */
    public List<RosterCard> getOneTimeCardsInRosterOrder() {
        return oneTimeCardsInRosterOrder;
    }

    /**
     * Returns every present card, grouped as reusable cards then One-Time copies.
     *
     * @return unmodifiable complete-card list
     */
    public List<RosterCard> getCards() {
        return cards;
    }

    /**
     * Returns deployable Active and One-Time cards using the Admiral's requested group priority.
     * Both groups retain natural Ship ordering from this snapshot.
     *
     * @param prioritizeReusable {@code true} for Active cards first; {@code false} for One-Time cards first
     * @return unmodifiable deployable-card list from this revision
     */
    public List<RosterCard> getDeployableCards(boolean prioritizeReusable) {
        return prioritizeReusable ? reusableFirstDeployableCards : oneTimeFirstDeployableCards;
    }

    /**
     * Returns the number of available One-Time copies for a canonical Ship type.
     *
     * @param ship Ship whose available quantity is requested
     * @return non-negative quantity, or zero when that One-Time Ship is absent
     * @throws NullPointerException if {@code ship} is null
     */
    public int getOneTimeQuantity(Ship ship) {
        Objects.requireNonNull(ship, "ship");
        return oneTimeQuantitiesByShipName.getOrDefault(ship.getName(), 0);
    }

    /**
     * Reports the mutually exclusive reusable state for a canonical Ship name in this snapshot.
     *
     * @param ship Ship whose reusable state is requested
     * @return Active, Maintenance, or Absent
     * @throws NullPointerException if {@code ship} is null
     */
    public RosterState getReusableState(Ship ship) {
        Objects.requireNonNull(ship, "ship");
        return reusableStatesByShipName.getOrDefault(ship.getName(), RosterState.ABSENT);
    }

    private static List<RosterCard> immutableCopy(List<RosterCard> cards) {
        return Collections.unmodifiableList(new ArrayList<RosterCard>(cards));
    }
}
