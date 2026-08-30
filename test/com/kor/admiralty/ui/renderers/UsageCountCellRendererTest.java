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
package com.kor.admiralty.ui.renderers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Component;

import javax.swing.JList;

import org.junit.jupiter.api.Test;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.beans.ShipUsageRow;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;

/**
 * Specifies Ship Statistics rendering through the immutable-row cell-renderer interface.
 */
class UsageCountCellRendererTest {

    /**
     * Verifies the renderer formats the row's deployment count and Roster membership.
     */
    @Test
    void rendererConsumesImmutableUsageRowState() {
        Ship ship = new ShipImpl(
                ShipFaction.Federation,
                Tier.Tier6,
                Rarity.Epic,
                Role.Eng,
                "Rendered Ship",
                50,
                40,
                30,
                RuleType.All.rewardBonus(0),
                "");
        ShipUsageRow row = new ShipUsageRow(ship, 12_345, false);
        UsageCountCellRenderer renderer = new UsageCountCellRenderer();

        Component component = renderer.getListCellRendererComponent(
                new JList<ShipUsageRow>(),
                row,
                0,
                false,
                false);

        assertSame(renderer, component);
        assertEquals("12,345", renderer.lblUsageCount.getText());
    }
}
