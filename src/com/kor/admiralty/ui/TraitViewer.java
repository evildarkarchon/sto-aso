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
import com.kor.admiralty.ui.resources.ActualShipIconFactory;
import com.kor.admiralty.ui.resources.Images;
import com.kor.admiralty.ui.resources.ShipIconFactory;
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
     * Creates the viewer from GameData that has already been published by
     * application bootstrap. Construction requires the Swing event-dispatch thread.
     *
     * @throws IllegalStateException if application bootstrap has not completed or
     *                               construction is off the event thread
     */
    public TraitViewer() {
        Swing.setLookAndFeel();
        setTitle(Title);
        setIconImage(Images.IMG_ASO);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(640, 480);
        getContentPane().setLayout(new BorderLayout(0, 0));

        getContentPane().add(presentation(
                App.gameData().ships(),
                new ActualShipIconFactory(App.iconCache())));
    }

    /**
     * Builds the established standalone GameData Starship Trait content without
     * requiring a native frame.
     *
     * @param ships        GameData Ships from which trait-bearing entries are shown
     * @param iconRenderer renderer for generic Ship artwork
     * @return named Ship Filter presentation for standalone Starship Traits
     * @throws NullPointerException if an argument or Ship is null
     * @throws IllegalStateException if called outside the event-dispatch thread
     */
    static ShipFilterView<Ship, ShipSortOrder> presentation(Collection<Ship> ships, ShipIconFactory iconRenderer) {
        return new ShipFilterViews(iconRenderer).gameDataStarshipTraits(ships);
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

            Swing.overrideComboBoxMouseWheel();
            // The shared presentation requires construction as well as display on the EDT.
            EventQueue.invokeLater(() -> new TraitViewer().run());
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
