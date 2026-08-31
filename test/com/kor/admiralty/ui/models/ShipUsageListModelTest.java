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

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.beans.ShipUsageRow;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.ShipUsageSortOrder;
import com.kor.admiralty.enums.Tier;

/**
 * Specifies Ship Statistics list behavior through its row-valued model
 * interface.
 */
class ShipUsageListModelTest {

    /**
     * Returns canonical names in the order exposed by the public Swing list-model
     * interface.
     *
     * @param model row-valued Ship Statistics model
     * @return visible canonical Ship names
     */
    private static List<String> visibleNames(ShipUsageListModel model) {
        return IntStream.range(0, model.getSize())
                .mapToObj(index -> model.getElementAt(index).ship().getName())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Creates canonical Ship facts for row-model sorting and filtering.
     *
     * @param name    canonical Ship name
     * @param faction Ship faction used by filters
     * @param tier    Ship tier used by default sorting
     * @return mutable GameData-style Ship facts that carry no deployment-count
     * state
     */
    private static Ship ship(String name, ShipFaction faction, Tier tier) {
        return new ShipImpl(
                faction,
                tier,
                Rarity.Common,
                Role.Eng,
                name,
                10,
                10,
                10,
                RuleType.All.rewardBonus(0),
                "");
    }

    /**
     * Verifies all statistics sort modes read immutable row counts and canonical
     * Ship facts.
     */
    @Test
    void sortModesConsumeUsageRows() {
        Ship alpha = ship("Alpha", ShipFaction.Federation, Tier.Tier6);
        Ship beta = ship("Beta", ShipFaction.Klingon, Tier.Tier1);
        Ship gamma = ship("Gamma", ShipFaction.Federation, Tier.Tier1);
        Ship delta = ship("Delta", ShipFaction.Federation, Tier.Tier6);
        ShipUsageRow alphaRow = new ShipUsageRow(alpha, 0, true);
        ShipUsageRow betaRow = new ShipUsageRow(beta, Integer.MAX_VALUE, false);
        ShipUsageRow gammaRow = new ShipUsageRow(gamma, 5, true);
        ShipUsageRow deltaRow = new ShipUsageRow(delta, 5, true);
        ShipUsageListModel model = new ShipUsageListModel(List.of(alphaRow, betaRow, deltaRow, gammaRow));

        model.setSortOrder(ShipUsageSortOrder.MostUsed);
        assertEquals(List.of("Beta", "Gamma", "Delta", "Alpha"), visibleNames(model));
        assertSame(betaRow, model.getElementAt(0));

        model.setSortOrder(ShipUsageSortOrder.LeastUsed);
        assertEquals(List.of("Alpha", "Gamma", "Delta", "Beta"), visibleNames(model));

        model.setSortOrder(ShipUsageSortOrder.Default);
        assertEquals(List.of("Beta", "Gamma", "Alpha", "Delta"), visibleNames(model));
    }

    /**
     * Verifies statistics filtering reads the canonical Ship nested in each
     * immutable row.
     */
    @Test
    void filtersUsageRowsByCanonicalShipFacts() {
        Ship federation = ship("Federation", ShipFaction.Federation, Tier.Tier6);
        Ship klingon = ship("Klingon", ShipFaction.Klingon, Tier.Tier6);
        ShipUsageListModel model = new ShipUsageListModel(List.of(
                new ShipUsageRow(federation, 2, true),
                new ShipUsageRow(klingon, 3, true)));

        model.setShowFederation(false);

        assertEquals(List.of("Klingon"), visibleNames(model));
    }
}
