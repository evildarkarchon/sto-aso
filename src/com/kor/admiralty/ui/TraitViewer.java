/*******************************************************************************
 * Copyright (C) 2015, 2019 Dave Kor
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/
package com.kor.admiralty.ui;

import com.kor.admiralty.App;
import com.kor.admiralty.AppBootstrap;
import com.kor.admiralty.AppBootstrapException;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.ShipSortOrder;
import com.kor.admiralty.ui.artwork.ShipArtwork;
import com.kor.admiralty.ui.resources.Images;
import com.kor.admiralty.ui.resources.Swing;
import com.kor.admiralty.ui.shipfilter.ShipFilterView;
import com.kor.admiralty.ui.shipfilter.ShipFilterViews;
import com.kor.admiralty.ui.workers.SwingWorkerExecutor;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;

import static com.kor.admiralty.ui.resources.Strings.AdmiraltyConsole.Title;

public class TraitViewer extends JFrame implements Runnable {

    @Serial
    private static final long serialVersionUID = -1956005915682128915L;

    /**
     * Creates the standalone GameData Starship Trait viewer with the artwork
     * lifetime supplied by its owning root. Construction requires the Swing
     * event-dispatch thread.
     *
     * @param ships canonical GameData Ships to present
     * @param artwork shared Ship Artwork for generic trait presentation
     * @throws NullPointerException if either argument or a Ship is null
     * @throws IllegalStateException if construction is off the event thread
     */
    public TraitViewer(Collection<Ship> ships, ShipArtwork artwork) {
        Swing.setLookAndFeel();
        setTitle(Title);
        setIconImage(Images.IMG_ASO);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(640, 480);
        getContentPane().setLayout(new BorderLayout(0, 0));

        getContentPane().add(presentation(ships, artwork));
    }

    /**
     * Builds the established standalone GameData Starship Trait content without
     * requiring a native frame.
     *
     * @param ships        GameData Ships from which trait-bearing entries are shown
     * @param shipArtwork  application-owned artwork for generic Ship presentation
     * @return named Ship Filter presentation for standalone Starship Traits
     * @throws NullPointerException  if an argument or Ship is null
     * @throws IllegalStateException if called outside the event-dispatch thread
     */
    static ShipFilterView<Ship, ShipSortOrder> presentation(Collection<Ship> ships, ShipArtwork shipArtwork) {
        return new ShipFilterViews(shipArtwork).gameDataStarshipTraits(ships);
    }

    /**
     * Bootstraps application data before constructing and scheduling the standalone
     * Trait Viewer.
     *
     * @param args ignored command-line arguments
     */
    static void main(String[] args) {
        try {
            Path workingDirectory = Path.of(System.getProperty("user.dir"));
            AppBootstrap bootstrap = new AppBootstrap(
                    AdmiraltyConsole.candidateExecutableDirectory(workingDirectory),
                    workingDirectory,
                    SwingWorkerExecutor.getInstance());
            bootstrap.bootstrap();

            Collection<Ship> ships = App.gameData().ships();
            ShipArtwork artwork = App.shipArtwork();
            Swing.overrideComboBoxMouseWheel();
            // The shared presentation requires construction as well as display on the EDT.
            EventQueue.invokeLater(() -> {
                try {
                    TraitViewer viewer = new TraitViewer(ships, artwork);
                    viewer.addWindowListener(new StandaloneArtworkCloseListener(artwork));
                    viewer.run();
                } catch (RuntimeException | Error failure) {
                    // A failed frame creation must not orphan the module opened by bootstrap.
                    try {
                        artwork.close();
                    } catch (RuntimeException closeFailure) {
                        failure.addSuppressed(closeFailure);
                    }
                    throw failure;
                }
            });
        } catch (AppBootstrapException | URISyntaxException cause) {
            AdmiraltyConsole.showStartupFailure(cause);
        }
    }

    /**
     * Shows this viewer on the Swing event-dispatch thread.
     */
    @Override
    public void run() {
        setVisible(true);
        toFront();
        repaint();
    }

}
