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
package com.kor.admiralty.ui;

import com.kor.admiralty.App;
import com.kor.admiralty.AppBootstrapException;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.ui.artwork.ShipArtwork;
import com.kor.admiralty.ui.shipfilter.ShipFilters;

import java.io.PrintStream;
import java.util.Collection;
import java.util.List;

/**
 * Standalone diagnostic for inspecting the headless canonical Ship Filter.
 */
public final class ShipFilterDiagnostic {

    /**
     * Prevents construction of the command-line diagnostic owner.
     */
    private ShipFilterDiagnostic() {
    }

    /**
     * Bootstraps GameData before printing the standalone Ship Filter diagnostic,
     * then closes the module owned by this short-lived application root.
     *
     * @param args ignored command-line arguments
     * @throws AppBootstrapException if application data cannot be loaded completely
     */
    static void main(String[] args) throws AppBootstrapException {
        AdmiraltyConsole.bootstrapApplication();
        run(App.shipArtwork(), App.gameData().ships(), System.out);
    }

    /**
     * Prints the headless projection inside the diagnostic root's owned artwork lifetime.
     *
     * @param artwork module opened during application bootstrap and closed on return
     * @param ships canonical Ships to inspect
     * @param output caller-owned destination for the diagnostic
     */
    static void run(ShipArtwork artwork, Collection<Ship> ships, PrintStream output) {
        try (artwork) {
            printShips(ships, output);
        }
    }

    /**
     * Prints the canonical projection and its visible/source totals without
     * constructing Swing controls. The caller retains ownership of the stream.
     *
     * @param ships  canonical Ships to project
     * @param output destination for numbered Ships and the count summary
     */
    static void printShips(Collection<Ship> ships, PrintStream output) {
        List<Ship> visible = ShipFilters.ships().project(ships);
        for (int i = 0; i < visible.size(); i++) {
            output.println((i + 1) + ": " + visible.get(i));
        }
        output.println(visible.size() + "/" + ships.size() + " ships.");
    }
}
