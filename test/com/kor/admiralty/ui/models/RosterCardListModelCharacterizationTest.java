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
package com.kor.admiralty.ui.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.io.GameData;

/**
 * Characterizes canonical Roster-card ordering without collapsing distinct
 * One-Time card identities.
 */
class RosterCardListModelCharacterizationTest {

    /**
     * Creates canonical Ship facts at one explicit tier.
     *
     * @param name canonical Ship name
     * @param tier canonical tier used by ordering
     * @return canonical test Ship
     */
    private static Ship ship(String name, Tier tier) {
        return new ShipImpl(
                ShipFaction.Federation,
                tier,
                Rarity.Common,
                Role.Eng,
                name,
                10,
                20,
                30,
                RuleType.All.rewardBonus(0),
                "");
    }

    /**
     * Returns every exact card exposed by the public list-model index.
     *
     * @param model sorted Roster-card model
     * @return visible card identities in presentation order
     */
    private static List<RosterCard> visibleCards(RosterCardListModel model) {
        return IntStream.range(0, model.getSize())
                .mapToObj(model::getElementAt)
                .toList();
    }

    /**
     * Returns all One-Time card identities for one canonical Ship.
     *
     * @param admiral owning Admiral
     * @param ship    canonical Ship shared by the cards
     * @return matching cards in Roster identity order
     */
    private static List<RosterCard> cardsFor(Admiral admiral, Ship ship) {
        return admiral.getRoster().getOneTimeCards().stream()
                .filter(card -> card.getShip() == ship)
                .toList();
    }

    /**
     * Verifies canonical Ship sorting surrounds comparator-equal cards while the
     * equal cards retain their caller-supplied identity order.
     */
    @Test
    void canonicalOrderingIsStableForEqualShipCardIdentities() {
        Ship tierOne = ship("Tier One", Tier.Tier1);
        Ship repeated = ship("Repeated", Tier.Tier3);
        Ship tierSix = ship("Tier Six", Tier.Tier6);
        GameData gameData = GameData.builder().ships(List.of(tierOne, repeated, tierSix)).build();
        Admiral admiral = new Admiral(gameData);
        admiral.adjustOneTimeShipQuantity(tierOne, 1);
        admiral.adjustOneTimeShipQuantity(repeated, 3);
        admiral.adjustOneTimeShipQuantity(tierSix, 1);
        RosterCard low = cardsFor(admiral, tierOne).getFirst();
        List<RosterCard> equalCards = cardsFor(admiral, repeated);
        RosterCard high = cardsFor(admiral, tierSix).getFirst();
        List<RosterCard> scrambledEqualCards = List.of(
                equalCards.get(2),
                equalCards.get(0),
                equalCards.get(1));
        List<RosterCard> input = new ArrayList<RosterCard>();
        input.add(high);
        input.addAll(scrambledEqualCards);
        input.add(low);
        RosterCardListModel model = new RosterCardListModel();

        model.setCards(input);

        List<RosterCard> visible = visibleCards(model);
        assertEquals(5, visible.size());
        assertSame(low, visible.get(0));
        assertSame(scrambledEqualCards.get(0), visible.get(1));
        assertSame(scrambledEqualCards.get(1), visible.get(2));
        assertSame(scrambledEqualCards.get(2), visible.get(3));
        assertSame(high, visible.get(4));
    }
}
