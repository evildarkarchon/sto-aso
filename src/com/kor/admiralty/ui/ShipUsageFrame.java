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
import com.kor.admiralty.AppBootstrapException;
import com.kor.admiralty.ui.resources.ActualShipIconFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.Beans;
import java.io.Serial;

import static com.kor.admiralty.ui.resources.Strings.ShipStatistics.Title;

/** Native window hosting the shared, testable Ship usage content. */
public final class ShipUsageFrame extends JFrame implements Runnable {

    @Serial
    private static final long serialVersionUID = -7842523811419803589L;

    /**
     * Creates the usage window after application bootstrap on the event-dispatch thread.
     *
     * @throws IllegalStateException if bootstrap is incomplete or construction is off the EDT
     */
    public ShipUsageFrame() {
        setTitle(Title);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        ShipUsagePanel content = new ShipUsagePanel(
                App.admirals(), new ActualShipIconFactory(App.iconCache()));
        getContentPane().add(content, BorderLayout.CENTER);
        addComponentListener(new ComponentAdapter() {
            /** Refreshes history changed while this window was hidden. */
            @Override
            public void componentShown(ComponentEvent event) {
                content.refresh();
            }
        });

        if (Beans.isDesignTime()) {
            setBounds(0, 0, 640, 480);
        } else {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int w = (int) (screen.getWidth() * 0.4);
            int h = (int) (screen.getHeight() - 34);
            int x = (int) ((screen.getWidth() - w) / 2);
            setSize(w, h);
            setLocation(x, 0);
        }
    }

    /**
     * Bootstraps shared application state before constructing and showing the
     * standalone usage frame.
     *
     * @param args ignored command-line arguments
     */
    static void main(String[] args) {
        try {
            AdmiraltyConsole.bootstrapApplication();
            // The named Ship Filter requires construction as well as display on the EDT.
            EventQueue.invokeLater(() -> {
                AdmiraltyConsole.STATS_FRAME = new ShipUsageFrame();
                AdmiraltyConsole.STATS_FRAME.run();
            });
        } catch (AppBootstrapException cause) {
            AdmiraltyConsole.showStartupFailure(cause);
        }
    }

    /** Toggles the usage window on the event-dispatch thread. */
    @Override
    public void run() {
        if (isVisible()) {
            setVisible(false);
        } else {
            setVisible(true);
            toFront();
            repaint();
        }
    }
}
