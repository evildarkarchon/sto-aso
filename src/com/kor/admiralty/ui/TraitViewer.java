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
import com.kor.admiralty.ui.components.JColumnList;
import com.kor.admiralty.ui.models.ShipListModel;
import com.kor.admiralty.ui.renderers.StarshipTraitCellRenderer;
import com.kor.admiralty.ui.resources.ActualShipIconFactory;
import com.kor.admiralty.ui.resources.Images;
import com.kor.admiralty.ui.resources.ShipIconFactory;
import com.kor.admiralty.ui.resources.Swing;
import com.kor.admiralty.ui.workers.SwingWorkerExecutor;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.kor.admiralty.ui.resources.Strings.AdmiraltyConsole.Title;

public class TraitViewer extends JFrame implements Runnable {

    @Serial
    private static final long serialVersionUID = -1956005915682128915L;

    protected JList<Ship> traitsList;
    protected ShipListModel traitsModel;
    protected StarshipTraitCellRenderer cellRenderer;

    /**
     * Creates the viewer from GameData that has already been published by
     * application bootstrap.
     *
     * @throws IllegalStateException if application bootstrap has not completed
     */
    public TraitViewer() {
        Swing.setLookAndFeel();
        setTitle(Title);
        setIconImage(Images.IMG_ASO);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(640, 480);
        getContentPane().setLayout(new BorderLayout(0, 0));

        TraitPresentation presentation = presentation(
                App.gameData().ships(),
                new ActualShipIconFactory(App.iconCache()));
        cellRenderer = presentation.renderer();
        traitsModel = presentation.model();
        traitsList = presentation.list();
        getContentPane().add(presentation.scrollPane());
    }

    /**
     * Builds the established standalone GameData Starship Trait content without
     * requiring a native frame.
     *
     * @param ships        GameData Ships from which trait-bearing entries are shown
     * @param iconRenderer renderer for generic Ship artwork
     * @return complete standalone trait presentation
     * @throws NullPointerException if an argument or Ship is null
     */
    static TraitPresentation presentation(Collection<Ship> ships, ShipIconFactory iconRenderer) {
        Objects.requireNonNull(ships, "ships");
        Objects.requireNonNull(iconRenderer, "iconRenderer");
        JScrollPane scrollPane = new JScrollPane();
        StarshipTraitCellRenderer renderer = new StarshipTraitCellRenderer(iconRenderer);
        ShipListModel model = new ShipListModel();
        JList<Ship> list = new JColumnList<Ship>(model);
        list.setLayoutOrientation(JList.VERTICAL);
        list.setCellRenderer(renderer);
        scrollPane.setViewportView(list);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        List<Ship> traitShips = new ArrayList<Ship>();
        for (Ship ship : ships) {
            Objects.requireNonNull(ship, "ship");
            if (ship.hasTrait()) {
                traitShips.add(ship);
            }
        }
        model.addShips(traitShips);
        return new TraitPresentation(scrollPane, model, list, renderer);
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
            EventQueue.invokeLater(new TraitViewer());
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

    /**
     * Groups the real standalone Starship Trait components so they can be attached
     * to a frame or characterized headlessly.
     *
     * @param scrollPane scrolling container shown by the frame
     * @param model      canonical Ship projection
     * @param list       vertical trait list
     * @param renderer   GameData Starship Trait renderer
     */
    record TraitPresentation(
            JScrollPane scrollPane,
            ShipListModel model,
            JList<Ship> list,
            StarshipTraitCellRenderer renderer) {

        /**
         * Rejects incomplete presentation groups before they can be published.
         */
        TraitPresentation {
            Objects.requireNonNull(scrollPane, "scrollPane");
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(list, "list");
            Objects.requireNonNull(renderer, "renderer");
        }
    }

}
