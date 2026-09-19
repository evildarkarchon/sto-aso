/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.*;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.resources.ShipIconFactory;
import com.kor.admiralty.ui.shipfilter.ShipFilterView;
import com.kor.admiralty.ui.shipfilter.ShipFilterViews;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies live pixels do not disturb the existing Ship Filter presentation. */
class ShipArtworkFilterLiveTest {
    @TempDir
    Path directory;

    /** Completes the renderer's artwork while preserving controls, model, and selected Ship identity. */
    @Test
    void completionLeavesExistingFilterAndSelectionUntouched() throws Exception {
        Ship ship = new ShipImpl(ShipFaction.Federation, Tier.Tier1, Rarity.Common, Role.None,
                "missing-filter-live", 0, 0, 0, RuleType.All.rewardBonus(0), "");
        AtomicReference<Consumer<BufferedImage>> completion = new AtomicReference<>();
        try (ShipArtwork artwork = new ShipArtwork(directory, GameData.builder().ships(List.of(ship)).build(),
                List.of(), name -> getClass().getResourceAsStream("/com/kor/admiralty/ui/resources/" + name),
                (name, done) -> completion.set(done))) {
            ImageIcon icon = artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC);
            AtomicReference<ShipFilterView<Ship, ShipSortOrder>> view = new AtomicReference<>();
            AtomicReference<JList<?>> entries = new AtomicReference<>();
            AtomicReference<ListModel<?>> model = new AtomicReference<>();
            AtomicReference<List<JCheckBox>> controls = new AtomicReference<>();
            AtomicReference<List<Boolean>> criteria = new AtomicReference<>();
            AtomicInteger events = new AtomicInteger();
            SwingUtilities.invokeAndWait(() -> {
                // This bridge is temporary until production callers migrate to Ship Artwork.
                ShipIconFactory renderer = (name, faction, role, rarity, owned) -> icon;
                view.set(new ShipFilterViews(renderer).reusableShipSelection(PlayerFaction.Federation, List.of(ship)));
                entries.set(children(view.get(), JList.class).getFirst());
                controls.set(children(view.get(), JCheckBox.class));
                controls.get().stream().filter(control -> control.getText().equals("Tactical"))
                        .findFirst().orElseThrow().doClick();
                criteria.set(controls.get().stream().map(AbstractButton::isSelected).toList());
                entries.get().setSelectedIndex(0);
                model.set(entries.get().getModel());
                model.get().addListDataListener(countEvents(events));
                entries.get().setSize(350, 100);
                BufferedImage canvas = new BufferedImage(350, 100, BufferedImage.TYPE_INT_ARGB);
                var graphics = canvas.createGraphics();
                try {
                    entries.get().paint(graphics);
                } finally {
                    graphics.dispose();
                }
            });

            BufferedImage source = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            var graphics = source.createGraphics();
            try {
                graphics.setColor(Color.MAGENTA);
                graphics.fillRect(0, 0, 64, 64);
            } finally {
                graphics.dispose();
            }
            completion.get().accept(source);

            SwingUtilities.invokeAndWait(() -> {
                assertEquals(Color.MAGENTA.getRGB(), ((BufferedImage) icon.getImage()).getRGB(32, 32));
                assertSame(icon, artwork.forShip(ship, ShipArtwork.Presentation.SPECIFIC));
                assertSame(entries.get(), children(view.get(), JList.class).getFirst());
                assertSame(model.get(), entries.get().getModel());
                assertSame(ship, entries.get().getSelectedValue());
                assertEquals(1, model.get().getSize());
                assertSame(ship, model.get().getElementAt(0));
                assertEquals(0, events.get());
                assertEquals(controls.get(), children(view.get(), JCheckBox.class));
                assertEquals(criteria.get(), controls.get().stream().map(AbstractButton::isSelected).toList());
            });
        }
    }

    /** Counts every kind of model notification to detect any artwork-driven projection update. */
    private static ListDataListener countEvents(AtomicInteger count) {
        return new ListDataListener() {
            /** Counts inserted rows. */
            @Override
            public void intervalAdded(ListDataEvent event) {
                count.incrementAndGet();
            }

            /** Counts removed rows. */
            @Override
            public void intervalRemoved(ListDataEvent event) {
                count.incrementAndGet();
            }

            /** Counts changed rows. */
            @Override
            public void contentsChanged(ListDataEvent event) {
                count.incrementAndGet();
            }
        };
    }

    /** Finds real presentation components through their public Swing containment hierarchy. */
    private static <T extends Component> List<T> children(Container parent, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component component : parent.getComponents()) {
            if (type.isInstance(component)) {
                found.add(type.cast(component));
            }
            if (component instanceof Container container) {
                found.addAll(children(container, type));
            }
        }
        return found;
    }
}
