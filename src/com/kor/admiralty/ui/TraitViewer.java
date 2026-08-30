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

import static com.kor.admiralty.ui.resources.Strings.AdmiraltyConsole.Title;

import java.awt.EventQueue;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;

import com.kor.admiralty.App;
import com.kor.admiralty.AppBootstrap;
import com.kor.admiralty.AppBootstrapException;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.ui.components.JColumnList;
import com.kor.admiralty.ui.models.ShipListModel;
import com.kor.admiralty.ui.renderers.StarshipTraitCellRenderer;
import com.kor.admiralty.ui.resources.Images;
import com.kor.admiralty.ui.resources.Swing;
import com.kor.admiralty.ui.workers.SwingWorkerExecutor;

import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import javax.swing.JList;

public class TraitViewer extends JFrame implements Runnable {

    private static final long serialVersionUID = -1956005915682128915L;

    protected JList<Ship> traitsList;
    protected ShipListModel traitsModel;
    protected StarshipTraitCellRenderer cellRenderer;

    /**
     * Creates the viewer from GameData that has already been published by application bootstrap.
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

        JScrollPane traitsScroll = new JScrollPane();
        getContentPane().add(traitsScroll);

        cellRenderer = new StarshipTraitCellRenderer();
        traitsModel = new ShipListModel();
        traitsList = new JColumnList<Ship>(traitsModel);
        traitsList.setLayoutOrientation(JList.VERTICAL);
        traitsList.setCellRenderer(cellRenderer);
        traitsScroll.setViewportView(traitsList);
        traitsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        List<Ship> ships = new ArrayList<Ship>();
        for (Ship ship : App.gameData().ships()) {
            if (ship.hasTrait()) {
                ships.add(ship);
            }
        }
        traitsModel.addShips(ships);
    }

    /**
     * Bootstraps application data before constructing and scheduling the standalone Trait Viewer.
     *
     * @param args ignored command-line arguments
     */
    public static void main(String[] args) {
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

}
