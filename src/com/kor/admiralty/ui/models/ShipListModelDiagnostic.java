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
package com.kor.admiralty.ui.models;

import com.kor.admiralty.App;
import com.kor.admiralty.AppBootstrapException;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.ui.AdmiraltyConsole;

import java.util.Collection;

/**
 * Standalone diagnostic for inspecting the filtered canonical Ship model.
 */
public final class ShipListModelDiagnostic {

    /**
     * Prevents construction of the command-line diagnostic owner.
     */
    private ShipListModelDiagnostic() {
    }

    /**
     * Bootstraps GameData before printing the standalone model diagnostic.
     *
     * @param args ignored command-line arguments
     * @throws AppBootstrapException if application data cannot be loaded completely
     */
    static void main(String[] args) throws AppBootstrapException {
        AdmiraltyConsole.bootstrapApplication();
        Collection<Ship> ships = App.gameData().ships();
        ShipListModel model = new ShipListModel(ships);
        // model.setShowFederation(false);
        for (int i = 1; i < model.getSize(); i++) {
            Ship ship = model.getElementAt(i);
            IO.println(i + ": " + ship);
        }
        IO.println(model.getSize() + "/" + ships.size() + " ships.");
    }
}
