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

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.beans.ShipUsageRow;
import com.kor.admiralty.enums.*;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.artwork.ShipArtwork;
import com.kor.admiralty.ui.artwork.ShipArtworkTestFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Specifies Ship Statistics rendering through the immutable-row cell-renderer
 * interface.
 */
class UsageCountCellRendererTest {

    @TempDir
    Path directory;

    /**
     * Verifies the renderer formats the row's deployment count and Roster
     * membership.
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
        ShipUsageRow row = new ShipUsageRow(ship, 12_345, false, false);
        try (ShipArtwork artwork = ShipArtworkTestFixture.offline(directory, List.of(ship))) {
            UsageCountCellRenderer renderer = new UsageCountCellRenderer(artwork);
            Component component = renderer.getListCellRendererComponent(
                    new JList<ShipUsageRow>(), row, 0, false, false);

            assertSame(renderer, component);
            assertEquals("12,345", renderer.lblUsageCount.getText());
        }
    }

    /**
     * A Ship present only as a One-Time card stays on generic artwork in Ship
     * Statistics even though its usage row belongs to the current Roster.
     */
    @Test
    void oneTimeOnlyUsageRowDisplaysGenericArtwork() {
        Ship ship = new ShipImpl(
                ShipFaction.Federation,
                Tier.Tier6,
                Rarity.Epic,
                Role.Eng,
                "One-Time Statistics Ship",
                50,
                40,
                30,
                RuleType.All.rewardBonus(0),
                "");
        GameData gameData = GameData.builder().ships(List.of(ship)).build();
        Admirals admirals = new Admirals(gameData);
        Admiral admiral = admirals.getAdmirals().getFirst();
        admiral.adjustOneTimeShipQuantity(ship, 1);
        ShipUsageRow row = admirals.getShipUsageRows(admiral).getFirst();
        List<String> requests = new ArrayList<>();
        try (ShipArtwork artwork = ShipArtworkTestFixture.scripted(directory, List.of(ship),
                (name, completed) -> {
                    requests.add(name);
                    completed.accept(null);
                })) {
            UsageCountCellRenderer renderer = new UsageCountCellRenderer(artwork);
            renderer.getListCellRendererComponent(new JList<ShipUsageRow>(), row, 0, false, false);

            ImageIcon generic = artwork.forShip(ship, ShipArtwork.Presentation.GENERIC);
            assertTrue(row.inCurrentRoster());
            assertTrue(Arrays.stream(renderer.shipRenderer.getComponents())
                    .anyMatch(component -> component instanceof JLabel label && label.getIcon() == generic));
            assertEquals(List.of(), requests);
        }
    }
}
