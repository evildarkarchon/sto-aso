/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterState;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.*;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.ShipDetailsPanel;
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

    /**
     * Replaces reusable-card pixels while retaining both Ship Filter projections,
     * the selected identities, and the separate generic details layout.
     */
    @Test
    void completionLeavesExistingFilterAndSelectionUntouched() throws Exception {
        Ship alpha = ship("Alpha", Role.Tac);
        Ship beta = ship("Beta", Role.Eng);
        Ship gamma = ship("Gamma", Role.Eng);
        GameData gameData = GameData.builder().ships(List.of(alpha, beta, gamma)).build();
        Admiral admiral = new Admiral(gameData);
        admiral.addReusableShips(List.of(alpha, beta, gamma), RosterState.ACTIVE);
        List<RosterCard> cards = admiral.getRoster().getReusableCards();
        RosterCard selectedCard = cards.stream().filter(card -> card.getShip() == beta).findFirst().orElseThrow();
        AtomicReference<Consumer<BufferedImage>> completion = new AtomicReference<>();
        try (ShipArtwork artwork = ShipArtworkTestFixture.scripted(directory, gameData,
                (name, done) -> {
                    if (name.equals(beta.getIconName())) {
                        completion.set(done);
                    } else {
                        done.accept(null);
                    }
                })) {
            ImageIcon icon = artwork.forShip(beta, ShipArtwork.Presentation.SPECIFIC);
            AtomicReference<ShipFilterView<RosterCard, ShipSortOrder>> rosterView = new AtomicReference<>();
            AtomicReference<ShipFilterView<Ship, ShipSortOrder>> detailsView = new AtomicReference<>();
            AtomicReference<JList<?>> rosterEntries = new AtomicReference<>();
            AtomicReference<JList<?>> detailEntries = new AtomicReference<>();
            AtomicReference<ShipDetailsPanel> details = new AtomicReference<>();
            AtomicReference<ListModel<?>> rosterModel = new AtomicReference<>();
            AtomicReference<ListModel<?>> detailModel = new AtomicReference<>();
            AtomicReference<List<?>> visible = new AtomicReference<>();
            AtomicReference<List<JCheckBox>> controls = new AtomicReference<>();
            AtomicReference<List<Boolean>> criteria = new AtomicReference<>();
            AtomicInteger events = new AtomicInteger();
            AtomicInteger detailEvents = new AtomicInteger();
            SwingUtilities.invokeAndWait(() -> {
                ShipFilterViews views = new ShipFilterViews(artwork);
                rosterView.set(views.rosterCardSelection(cards));
                detailsView.set(views.reusableShipSelection(
                        PlayerFaction.Federation, List.of(gamma, alpha, beta)));
                rosterEntries.set(children(rosterView.get(), JList.class).getFirst());
                detailEntries.set(children(detailsView.get(), JList.class).getFirst());
                details.set(children(detailsView.get(), ShipDetailsPanel.class).getFirst());
                controls.set(children(rosterView.get(), JCheckBox.class));
                controls.get().stream().filter(control -> control.getText().equals("Tactical"))
                        .findFirst().orElseThrow().doClick();
                criteria.set(controls.get().stream().map(AbstractButton::isSelected).toList());
                rosterEntries.get().setSelectedValue(selectedCard, false);
                detailEntries.get().setSelectedValue(beta, false);
                rosterModel.set(rosterEntries.get().getModel());
                detailModel.set(detailEntries.get().getModel());
                visible.set(visible(rosterModel.get()));
                assertEquals(List.of(selectedCard, cards.stream().filter(card -> card.getShip() == gamma)
                        .findFirst().orElseThrow()), visible.get());
                assertInstanceOf(GridBagLayout.class, detailsView.get().getLayout());
                assertEquals(2, detailsView.get().getComponentCount());
                assertTrue(children(details.get(), JLabel.class).stream()
                        .anyMatch(label -> label.getIcon() == artwork.forShip(beta, ShipArtwork.Presentation.GENERIC)));
                rosterModel.get().addListDataListener(countEvents(events));
                detailModel.get().addListDataListener(countEvents(detailEvents));
                rosterEntries.get().setSize(350, 200);
                BufferedImage canvas = new BufferedImage(350, 200, BufferedImage.TYPE_INT_ARGB);
                var graphics = canvas.createGraphics();
                try {
                    rosterEntries.get().paint(graphics);
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
            assertNotNull(completion.get());
            completion.get().accept(source);

            SwingUtilities.invokeAndWait(() -> {
                assertEquals(Color.MAGENTA.getRGB(), ((BufferedImage) icon.getImage()).getRGB(32, 32));
                assertSame(icon, artwork.forShip(beta, ShipArtwork.Presentation.SPECIFIC));
                assertSame(rosterEntries.get(), children(rosterView.get(), JList.class).getFirst());
                assertSame(rosterModel.get(), rosterEntries.get().getModel());
                assertEquals(visible.get(), visible(rosterModel.get()));
                assertSame(selectedCard, rosterEntries.get().getSelectedValue());
                assertEquals(0, events.get());
                assertEquals(controls.get(), children(rosterView.get(), JCheckBox.class));
                assertEquals(criteria.get(), controls.get().stream().map(AbstractButton::isSelected).toList());
                assertSame(detailEntries.get(), children(detailsView.get(), JList.class).getFirst());
                assertSame(detailModel.get(), detailEntries.get().getModel());
                assertSame(beta, detailEntries.get().getSelectedValue());
                assertSame(details.get(), children(detailsView.get(), ShipDetailsPanel.class).getFirst());
                assertInstanceOf(GridBagLayout.class, detailsView.get().getLayout());
                assertEquals(2, detailsView.get().getComponentCount());
                assertEquals(0, detailEvents.get());
            });
        }
    }

    /** Creates a canonical Ship with a role that one filter control can hide. */
    private static Ship ship(String name, Role role) {
        return new ShipImpl(ShipFaction.Federation, Tier.Tier1, Rarity.Common, role,
                name, 0, 0, 0, RuleType.All.rewardBonus(0), "");
    }

    /** Captures exact visible entry identities in their current list order. */
    private static List<?> visible(ListModel<?> model) {
        List<Object> entries = new ArrayList<>();
        for (int index = 0; index < model.getSize(); index++) {
            entries.add(model.getElementAt(index));
        }
        return List.copyOf(entries);
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
