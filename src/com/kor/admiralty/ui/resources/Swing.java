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
package com.kor.admiralty.ui.resources;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;

public class Swing {

    public static final Color ColorBackground = new Color(8, 11, 20);
    public static final Color ColorBackgroundHighlighted = ColorBackground.brighter().brighter().brighter();
    public static final Color ColorBorders = new Color(15, 22, 30);
    public static final Color ColorBordersHighlighted = ColorBorders.brighter().brighter().brighter();
    public static final Border BorderDefault = new LineBorder(ColorBorders);
    public static final Border BorderHighlighted = new LineBorder(ColorBordersHighlighted);
    public static Color ColorCommon = new Color(255, 255, 255);
    public static Color ColorUncommon = new Color(0, 204, 0);
    public static Color ColorRare = new Color(0, 153, 255);
    public static Color ColorVeryRare = new Color(162, 69, 185);
    public static Color ColorUltraRare = new Color(109, 101, 188);
    public static Color ColorEpic = new Color(255, 165, 0);

    public static void setLookAndFeel() {
        try {
            for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            try {
                String lnfClass = UIManager.getSystemLookAndFeelClassName();
                UIManager.setLookAndFeel(lnfClass);
            } catch (Throwable e1) {
                e1.printStackTrace();
            }
        }
    }

    public static void overrideComboBoxMouseWheel() {
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            public void eventDispatched(AWTEvent event) {
                // Code to enable mousewheel scrolling to work in combobox
                // drop-down menus
                if (event instanceof MouseWheelEvent wheelEvent) {
                    Object source = event.getSource();

                    if (source instanceof JScrollPane scroll) {
                        String name = scroll.getName();
                        if (name != null && name.equals("ComboBox.scrollPane")) {
                            MouseWheelEvent sourceEvent = wheelEvent;

                            for (MouseWheelListener listener : scroll.getListeners(MouseWheelListener.class)) {
                                listener.mouseWheelMoved(sourceEvent);
                            }

                            sourceEvent.consume();
                        }
                    }

                }
            }
        }, AWTEvent.MOUSE_WHEEL_EVENT_MASK);
    }

    public static void setFont(Component component, int style, float size) {
        component.setFont(component.getFont().deriveFont(style, size));
    }

    /**
     * Applies the established 70%-of-screen preferred height to a dialog content
     * panel while leaving it constructible when tests run without a screen.
     *
     * @param component dialog content whose preferred height should be configured
     * @throws NullPointerException if {@code component} is null
     */
    public static void configureScreenRelativeDialogHeight(JComponent component) {
        java.util.Objects.requireNonNull(component, "component");
        Dimension preferredSize = component.getPreferredSize();
        if (!GraphicsEnvironment.isHeadless()) {
            // Headless characterization tests have no screen, while desktop dialogs keep
            // their established screen-relative height.
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            preferredSize.height = (int) (screenSize.height * 0.7f);
        }
        component.setPreferredSize(preferredSize);
    }

    /**
     * Rejects lifecycle, interaction, or projection work outside the Swing event
     * thread.
     *
     * @param operation human-readable operation for diagnostics
     * @throws IllegalStateException if called outside the Swing event thread
     */
    public static void requireEventDispatchThread(String operation) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Must " + operation + " on the Swing event thread");
        }
    }

}
