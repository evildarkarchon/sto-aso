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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable structured information about one committed Solution deployment.
 */
public final class Deployment implements DeploymentOutcome {

    private final List<RosterCard> cards;
    private final List<RosterCard> reusableCards;
    private final List<RosterCard> oneTimeCards;
    private final RosterChange rosterChange;

    /**
     * Captures the exact deployed card identities and the single Roster change that
     * committed them.
     *
     * @param cards        deployed cards in Assignment and slot order
     * @param rosterChange committed before/after Roster snapshots
     * @throws NullPointerException if an argument or card is null
     */
    Deployment(List<RosterCard> cards, RosterChange rosterChange) {
        Objects.requireNonNull(cards, "cards");
        List<RosterCard> copiedCards = new ArrayList<RosterCard>(cards.size());
        for (RosterCard card : cards) {
            copiedCards.add(Objects.requireNonNull(card, "cards contains null"));
        }
        this.cards = Collections.unmodifiableList(copiedCards);
        reusableCards = cardsOfKind(copiedCards, RosterCardKind.REUSABLE);
        oneTimeCards = cardsOfKind(copiedCards, RosterCardKind.ONE_TIME);
        this.rosterChange = Objects.requireNonNull(rosterChange, "rosterChange");
    }

    /**
     * Filters a copied card list without exposing mutable stream results.
     *
     * @param cards copied deployed cards
     * @param kind  requested card kind
     * @return immutable filtered list
     */
    private static List<RosterCard> cardsOfKind(List<RosterCard> cards, RosterCardKind kind) {
        return Collections.unmodifiableList(cards.stream()
                .filter(card -> card.getKind() == kind)
                .collect(Collectors.toList()));
    }

    /**
     * Returns every exact deployed card in Assignment and slot order.
     *
     * @return immutable deployed-card list
     */
    public List<RosterCard> getCards() {
        return cards;
    }

    /**
     * Returns the reusable cards moved from Active to Maintenance.
     *
     * @return immutable reusable-card list
     */
    public List<RosterCard> getReusableCards() {
        return reusableCards;
    }

    /**
     * Returns the One-Time cards consumed by this deployment.
     *
     * @return immutable One-Time-card list
     */
    public List<RosterCard> getOneTimeCards() {
        return oneTimeCards;
    }

    /**
     * Returns the one committed Roster transition published for the whole
     * deployment.
     *
     * @return immutable before/after Roster change
     */
    public RosterChange getRosterChange() {
        return rosterChange;
    }
}
