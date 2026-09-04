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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.kor.admiralty.ui.shipfilter;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.PlayerFaction;
import com.kor.admiralty.enums.ShipSortOrder;
import com.kor.admiralty.ui.renderers.ShipCellRenderer;
import com.kor.admiralty.ui.resources.ShipIconFactory;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.kor.admiralty.ui.resources.Strings.ShipSelectionPanel.LabelCancel;
import static com.kor.admiralty.ui.resources.Strings.ShipSelectionPanel.LabelOkay;

/**
 * Internal modal boundary used to exercise dialog outcomes without opening a
 * native window in focused tests.
 */
@FunctionalInterface
interface ShipFilterDialog {

    /**
     * Presents one configured selection surface and returns a Swing option code.
     *
     * @param owner   owning workspace window
     * @param content configured selection content
     * @param title   dialog title
     * @return Swing option result
     */
    int show(Window owner, ShipFilterView<?, ?> content, String title);
}

/**
 * Named production paths for Swing presentations backed by the Ship Filter
 * module. Renderer and layout choices remain owned by this factory.
 */
public final class ShipFilterViews {

    private final ShipIconFactory iconRenderer;
    private final ShipFilterDialog dialog;

    /**
     * Binds the artwork adapter shared by views created from this factory.
     *
     * @param iconRenderer renderer for canonical Ship artwork
     * @throws NullPointerException if {@code iconRenderer} is null
     */
    public ShipFilterViews(ShipIconFactory iconRenderer) {
        this(iconRenderer, ShipFilterViews::showOptionDialog);
    }

    /**
     * Binds deterministic modal behavior for focused presentation tests while
     * production continues to use the native Swing option dialog.
     *
     * @param iconRenderer renderer for canonical Ship artwork
     * @param dialog       modal option boundary
     */
    ShipFilterViews(ShipIconFactory iconRenderer, ShipFilterDialog dialog) {
        this.iconRenderer = Objects.requireNonNull(iconRenderer, "iconRenderer");
        this.dialog = Objects.requireNonNull(dialog, "dialog");
    }

    /**
     * Shows the established native reusable-Ship option dialog.
     *
     * @param owner   owning workspace window
     * @param content configured Ship Filter presentation
     * @param title   action-specific dialog title
     * @return Swing option outcome
     */
    private static int showOptionDialog(Window owner, ShipFilterView<?, ?> content, String title) {
        return JOptionPane.showOptionDialog(
                owner,
                content,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                new String[]{LabelOkay, LabelCancel},
                LabelOkay);
    }

    /**
     * Creates reusable Ship selection with the complete module-owned Admiral
     * faction profile and canonical Ship ordering.
     *
     * @param faction    Admiral faction selecting the initial profile
     * @param candidates reusable Ship candidates from GameData
     * @return configured reusable Ship selection presentation
     * @throws IllegalStateException if called outside the event-dispatch thread
     * @throws NullPointerException  if an argument or required Ship fact is null
     */
    public ShipFilterView<Ship, ShipSortOrder> reusableShipSelection(
            PlayerFaction faction,
            Collection<? extends Ship> candidates) {
        return new ShipFilterView<Ship, ShipSortOrder>(
                ShipFilters.shipsForAdmiral(Objects.requireNonNull(faction, "faction")),
                Objects.requireNonNull(candidates, "candidates"),
                new ShipCellRenderer(iconRenderer),
                true);
    }

    /**
     * Opens the named reusable Ship selection path and returns an immutable
     * visible-order selection only after explicit acceptance.
     *
     * @param owner      owning workspace window, or {@code null} before attachment
     * @param faction    Admiral faction selecting the complete initial profile
     * @param candidates reusable Ship candidates from GameData
     * @param title      action-specific dialog title
     * @return immutable accepted Ships in visible order, or an empty immutable list
     * @throws IllegalStateException if called outside the event-dispatch thread
     * @throws NullPointerException  if a required argument or Ship fact is null
     */
    public List<Ship> chooseReusableShips(
            Window owner,
            PlayerFaction faction,
            Collection<? extends Ship> candidates,
            String title) {
        Objects.requireNonNull(title, "title");
        ShipFilterView<Ship, ShipSortOrder> view = reusableShipSelection(faction, candidates);
        int option = dialog.show(owner, view, title);
        return option == JOptionPane.OK_OPTION ? view.selectedEntries() : List.of();
    }
}
