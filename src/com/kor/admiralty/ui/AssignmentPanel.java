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

import com.kor.admiralty.Globals;
import com.kor.admiralty.beans.*;
import com.kor.admiralty.beans.Event;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.renderers.ShipCellRenderer;
import com.kor.admiralty.ui.resources.ShipIconFactory;
import com.kor.admiralty.ui.resources.Swing;
import com.kor.admiralty.ui.util.AutoCompletion;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.Beans;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serial;
import java.text.NumberFormat;
import java.util.Hashtable;
import java.util.Objects;
import java.util.function.Consumer;

import static com.kor.admiralty.ui.resources.Strings.AssignmentPanel.*;

public class AssignmentPanel extends JPanel implements FocusListener, PropertyChangeListener {

    public static final Color COLOR_YELLOW = new Color(254, 231, 117).darker().darker();
    public static final int MIN_CRITCHANCE = 0;
    public static final int MAX_CRITCHANCE = 80;
    @Serial
    private static final long serialVersionUID = -8480574144082649994L;
    private static final Consumer<AssignmentView> NO_ASSIGNMENT_INTENT = ignored -> {
        // Unbound editors intentionally discard control events emitted during disposal.
    };
    private final JPanel panel_1;
    private final JLabel lblScore;
    private final JComboBox<AdmAssignment> cbxAssignment;
    private final JComboBox<Event> cbxEvent;
    private final JSlider sliTargetCritChance;
    private final JTabbedPane tabbedPane;
    private final JPanel pnlShips;
    private final JPanel panel;
    private final JLabel lblTargetCritChance;
    private final GameData gameData;
    protected Assignment assignment;
    protected AssignmentView assignmentView;
    protected AssignmentSolution solution;
    protected NumberFormat intFormat;
    protected JFormattedTextField txtAssignmentEng;
    protected JFormattedTextField txtEventEng;
    protected JFormattedTextField txtAssignmentTac;
    protected JFormattedTextField txtEventTac;
    protected JFormattedTextField txtAssignmentSci;
    protected JFormattedTextField txtEventSci;
    protected JFormattedTextField txtEventCritRating;
    protected JLabel lblSlottedEng;
    protected JLabel lblSlottedTac;
    protected JLabel lblSlottedSci;
    protected JLabel lblSlottedCritRating;
    protected ShipCellRenderer pnlShip1;
    protected ShipCellRenderer pnlShip2;
    protected ShipCellRenderer pnlShip3;
    private Consumer<AssignmentView> assignmentIntent = NO_ASSIGNMENT_INTENT;
    private boolean projectingAssignment;

    /**
     * Creates an Assignment editor bound to one model with explicit reference data
     * and Ship artwork rendering.
     *
     * @param assignment   Assignment model to edit
     * @param gameData     reference data used by Assignment and Event lookup
     * @param iconRenderer renderer used by slotted Ship cards
     * @throws NullPointerException  if any dependency is {@code null}
     * @throws IllegalStateException if construction occurs outside the Swing event thread
     */
    public AssignmentPanel(Assignment assignment, GameData gameData, ShipIconFactory iconRenderer) {
        this(gameData, iconRenderer);
        setAssignment(assignment);
        clearSolutions();
    }

    /**
     * Creates an unbound Assignment editor with explicit lookup and artwork dependencies.
     * The caller must bind an Assignment before user interaction.
     *
     * @param gameData     reference data used by Assignment and Event lookup
     * @param iconRenderer renderer used by slotted Ship cards
     * @throws NullPointerException if either dependency is {@code null}
     */
    public AssignmentPanel(GameData gameData, ShipIconFactory iconRenderer) {
        this.gameData = Objects.requireNonNull(gameData, "gameData");
        Objects.requireNonNull(iconRenderer, "iconRenderer");
        intFormat = NumberFormat.getIntegerInstance();
        solution = null;

        setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));

        GridBagLayout gridBagLayout = new GridBagLayout();
        gridBagLayout.columnWidths = new int[]{0};
        gridBagLayout.rowHeights = new int[]{0, 0, 0, 0};
        gridBagLayout.columnWeights = new double[]{1.0};
        gridBagLayout.rowWeights = new double[]{1.0, 1.0, 0.0, 0.0};
        setLayout(gridBagLayout);

        cbxAssignment = new JComboBox<AdmAssignment>();
        AutoCompletion.enable(cbxAssignment);
        Swing.setFont(cbxAssignment, Font.PLAIN, 16);
        GridBagConstraints gbc_cbxAssignment = new GridBagConstraints();
        gbc_cbxAssignment.gridwidth = 4;
        gbc_cbxAssignment.weighty = 1.0;
        gbc_cbxAssignment.insets = new Insets(5, 5, 5, 0);
        gbc_cbxAssignment.fill = GridBagConstraints.HORIZONTAL;
        gbc_cbxAssignment.gridx = 0;
        gbc_cbxAssignment.gridy = 0;
        add(cbxAssignment, gbc_cbxAssignment);

        panel = new JPanel();
        GridBagConstraints gbc_panel = new GridBagConstraints();
        gbc_panel.fill = GridBagConstraints.BOTH;
        gbc_panel.gridwidth = 4;
        gbc_panel.insets = new Insets(0, 0, 5, 5);
        gbc_panel.gridx = 0;
        gbc_panel.gridy = 1;
        add(panel, gbc_panel);
        GridBagLayout gbl_panel = new GridBagLayout();
        gbl_panel.columnWidths = new int[]{0, 0, 0};
        gbl_panel.rowHeights = new int[]{0};
        gbl_panel.columnWeights = new double[]{1.0, 0.0, 0.0};
        gbl_panel.rowWeights = new double[]{1.0};
        panel.setLayout(gbl_panel);

        cbxEvent = new JComboBox<Event>();
        AutoCompletion.enable(cbxEvent);
        GridBagConstraints gbc_cbxEvent = new GridBagConstraints();
        gbc_cbxEvent.anchor = GridBagConstraints.NORTH;
        gbc_cbxEvent.weighty = 1.0;
        gbc_cbxEvent.weightx = 1.0;
        gbc_cbxEvent.insets = new Insets(0, 5, 0, 5);
        gbc_cbxEvent.fill = GridBagConstraints.HORIZONTAL;
        gbc_cbxEvent.gridx = 0;
        gbc_cbxEvent.gridy = 0;
        panel.add(cbxEvent, gbc_cbxEvent);

        lblTargetCritChance = new JLabel(LabelCriticalChance);
        GridBagConstraints gbc_lblTargetCritChance = new GridBagConstraints();
        gbc_lblTargetCritChance.fill = GridBagConstraints.HORIZONTAL;
        gbc_lblTargetCritChance.anchor = GridBagConstraints.NORTH;
        gbc_lblTargetCritChance.insets = new Insets(5, 5, 0, 5);
        gbc_lblTargetCritChance.gridx = 1;
        gbc_lblTargetCritChance.gridy = 0;
        panel.add(lblTargetCritChance, gbc_lblTargetCritChance);

        Hashtable<Integer, JLabel> labels = new Hashtable<Integer, JLabel>();
        for (int i = MIN_CRITCHANCE; i <= MAX_CRITCHANCE; i += 10) {
            labels.put(i, new JLabel(String.format(Percent, i)));
        }

        sliTargetCritChance = new JSlider();
        sliTargetCritChance.setValue(MIN_CRITCHANCE);
        sliTargetCritChance.setMinimum(MIN_CRITCHANCE);
        sliTargetCritChance.setMaximum(MAX_CRITCHANCE);
        sliTargetCritChance.setLabelTable(labels);
        sliTargetCritChance.setSnapToTicks(true);
        sliTargetCritChance.setPaintTicks(true);
        sliTargetCritChance.setPaintLabels(true);
        sliTargetCritChance.setMinorTickSpacing(5);
        sliTargetCritChance.setMajorTickSpacing(10);
        GridBagConstraints gbc_sliTargetCritChance = new GridBagConstraints();
        gbc_sliTargetCritChance.fill = GridBagConstraints.HORIZONTAL;
        gbc_sliTargetCritChance.weightx = 1.0;
        gbc_sliTargetCritChance.insets = new Insets(0, 0, 0, 5);
        gbc_sliTargetCritChance.gridx = 2;
        gbc_sliTargetCritChance.gridy = 0;
        panel.add(sliTargetCritChance, gbc_sliTargetCritChance);

        lblSlottedEng = new JLabel(String.format(HtmlSlot, LabelENG, ColorPositive, 0, 0));
        Swing.setFont(lblSlottedEng, Font.BOLD, 12);
        GridBagConstraints gbc_lblSlottedEng = new GridBagConstraints();
        gbc_lblSlottedEng.weighty = 1.0;
        gbc_lblSlottedEng.weightx = 1.0;
        gbc_lblSlottedEng.insets = new Insets(5, 5, 5, 5);
        gbc_lblSlottedEng.gridx = 0;
        gbc_lblSlottedEng.gridy = 2;
        add(lblSlottedEng, gbc_lblSlottedEng);

        lblSlottedTac = new JLabel(String.format(HtmlSlot, LabelTAC, ColorPositive, 0, 0));
        Swing.setFont(lblSlottedTac, Font.BOLD, 12);
        GridBagConstraints gbc_lblSlottedTac = new GridBagConstraints();
        gbc_lblSlottedTac.weighty = 1.0;
        gbc_lblSlottedTac.weightx = 1.0;
        gbc_lblSlottedTac.insets = new Insets(5, 0, 5, 5);
        gbc_lblSlottedTac.gridx = 1;
        gbc_lblSlottedTac.gridy = 2;
        add(lblSlottedTac, gbc_lblSlottedTac);

        lblSlottedSci = new JLabel(String.format(HtmlSlot, LabelSCI, ColorPositive, 0, 0));
        Swing.setFont(lblSlottedSci, Font.BOLD, 12);
        GridBagConstraints gbc_lblSlottedSci = new GridBagConstraints();
        gbc_lblSlottedSci.weighty = 1.0;
        gbc_lblSlottedSci.weightx = 1.0;
        gbc_lblSlottedSci.insets = new Insets(5, 0, 5, 5);
        gbc_lblSlottedSci.gridx = 2;
        gbc_lblSlottedSci.gridy = 2;
        add(lblSlottedSci, gbc_lblSlottedSci);

        lblSlottedCritRating = new JLabel(String.format(HtmlSlot, labelCRIT, ColorPositive, 0, 0));
        Swing.setFont(lblSlottedCritRating, Font.BOLD, 12);
        GridBagConstraints gbc_lblSlottedCritRating = new GridBagConstraints();
        gbc_lblSlottedCritRating.weighty = 1.0;
        gbc_lblSlottedCritRating.weightx = 1.0;
        gbc_lblSlottedCritRating.insets = new Insets(5, 0, 5, 0);
        gbc_lblSlottedCritRating.gridx = 3;
        gbc_lblSlottedCritRating.gridy = 2;
        add(lblSlottedCritRating, gbc_lblSlottedCritRating);

        tabbedPane = new JTabbedPane(JTabbedPane.LEFT);
        GridBagConstraints gbc_tabbedPane = new GridBagConstraints();
        gbc_tabbedPane.gridwidth = 4;
        gbc_tabbedPane.weighty = 20.0;
        gbc_tabbedPane.insets = new Insets(5, 5, 0, 0);
        gbc_tabbedPane.fill = GridBagConstraints.BOTH;
        gbc_tabbedPane.gridx = 0;
        gbc_tabbedPane.gridy = 3;
        add(tabbedPane, gbc_tabbedPane);

        pnlShips = new JPanel();
        tabbedPane.addTab(TabAssignedShips, null, pnlShips, null);
        pnlShips.setLayout(new GridLayout(3, 1, 0, 1));

        pnlShip1 = new ShipCellRenderer(iconRenderer);
        pnlShips.add(pnlShip1);

        pnlShip2 = new ShipCellRenderer(iconRenderer);
        pnlShips.add(pnlShip2);

        pnlShip3 = new ShipCellRenderer(iconRenderer);
        pnlShips.add(pnlShip3);

        JPanel pnlStats = new JPanel();
        tabbedPane.addTab(TabAssignmentStats, null, pnlStats, null);
        pnlStats.setBorder(null);
        GridBagLayout gbl_pnlStats = new GridBagLayout();
        gbl_pnlStats.columnWidths = new int[]{0, 0, 0, 0, 0};
        gbl_pnlStats.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0, 30};
        gbl_pnlStats.columnWeights = new double[]{1.0, 1.0, 1.0, 0.0, Double.MIN_VALUE};
        gbl_pnlStats.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 0.0};
        pnlStats.setLayout(gbl_pnlStats);

        JLabel lblAssignment = new JLabel(LabelRequired);
        Swing.setFont(lblAssignment, Font.BOLD, 11);
        GridBagConstraints gbc_lblAssignment = new GridBagConstraints();
        gbc_lblAssignment.weightx = 1.0;
        gbc_lblAssignment.insets = new Insets(0, 0, 5, 5);
        gbc_lblAssignment.gridx = 1;
        gbc_lblAssignment.gridy = 2;
        pnlStats.add(lblAssignment, gbc_lblAssignment);

        JLabel lblEvent = new JLabel("Event");
        lblEvent.setFont(new Font("Tahoma", Font.BOLD, 11));
        GridBagConstraints gbc_lblEvent = new GridBagConstraints();
        gbc_lblEvent.weightx = 1.0;
        gbc_lblEvent.insets = new Insets(0, 0, 5, 5);
        gbc_lblEvent.gridx = 2;
        gbc_lblEvent.gridy = 2;
        pnlStats.add(lblEvent, gbc_lblEvent);

        JLabel lblSlotted = new JLabel("Slotted");
        lblSlotted.setFont(new Font("Tahoma", Font.BOLD, 11));
        GridBagConstraints gbc_lblSlotted = new GridBagConstraints();
        gbc_lblSlotted.weightx = 1.0;
        gbc_lblSlotted.insets = new Insets(0, 0, 5, 0);
        gbc_lblSlotted.gridx = 3;
        gbc_lblSlotted.gridy = 2;
        pnlStats.add(lblSlotted, gbc_lblSlotted);

        JLabel lblEng = new JLabel(LabelENG);
        lblEng.setFont(new Font("Tahoma", Font.BOLD, 11));
        GridBagConstraints gbc_lblEng = new GridBagConstraints();
        gbc_lblEng.weightx = 1.0;
        gbc_lblEng.anchor = GridBagConstraints.WEST;
        gbc_lblEng.insets = new Insets(0, 5, 5, 5);
        gbc_lblEng.gridx = 0;
        gbc_lblEng.gridy = 3;
        pnlStats.add(lblEng, gbc_lblEng);

        JLabel lblTac = new JLabel(LabelTAC);
        lblTac.setFont(new Font("Tahoma", Font.BOLD, 11));
        GridBagConstraints gbc_lblTac = new GridBagConstraints();
        gbc_lblTac.weightx = 1.0;
        gbc_lblTac.anchor = GridBagConstraints.WEST;
        gbc_lblTac.insets = new Insets(0, 5, 5, 5);
        gbc_lblTac.gridx = 0;
        gbc_lblTac.gridy = 4;
        pnlStats.add(lblTac, gbc_lblTac);

        JLabel lblSci = new JLabel(LabelSCI);
        lblSci.setFont(new Font("Tahoma", Font.BOLD, 11));
        GridBagConstraints gbc_lblSci = new GridBagConstraints();
        gbc_lblSci.weightx = 1.0;
        gbc_lblSci.anchor = GridBagConstraints.WEST;
        gbc_lblSci.insets = new Insets(0, 5, 5, 5);
        gbc_lblSci.gridx = 0;
        gbc_lblSci.gridy = 5;
        pnlStats.add(lblSci, gbc_lblSci);

        JLabel lblCritRating = new JLabel("Crit Rating");
        // lblCritRating.setVisible(false);
        lblCritRating.setFont(new Font("Tahoma", Font.BOLD, 11));
        GridBagConstraints gbc_lblCritRating = new GridBagConstraints();
        gbc_lblCritRating.weightx = 2.0;
        gbc_lblCritRating.anchor = GridBagConstraints.WEST;
        gbc_lblCritRating.gridwidth = 2;
        gbc_lblCritRating.insets = new Insets(0, 5, 5, 5);
        gbc_lblCritRating.gridx = 0;
        gbc_lblCritRating.gridy = 6;
        pnlStats.add(lblCritRating, gbc_lblCritRating);

        txtAssignmentEng = new JFormattedTextField(intFormat);
        txtAssignmentEng.addFocusListener(this);
        txtAssignmentEng.setText("0");
        GridBagConstraints gbc_txtAssignmentEng = new GridBagConstraints();
        gbc_txtAssignmentEng.weightx = 1.0;
        gbc_txtAssignmentEng.insets = new Insets(0, 0, 5, 5);
        gbc_txtAssignmentEng.gridx = 1;
        gbc_txtAssignmentEng.gridy = 3;
        pnlStats.add(txtAssignmentEng, gbc_txtAssignmentEng);
        txtAssignmentEng.setColumns(4);

        txtAssignmentTac = new JFormattedTextField(intFormat);
        txtAssignmentTac.addFocusListener(this);
        txtAssignmentTac.setText("0");
        GridBagConstraints gbc_txtAssignmentTac = new GridBagConstraints();
        gbc_txtAssignmentTac.weightx = 1.0;
        gbc_txtAssignmentTac.insets = new Insets(0, 0, 5, 5);
        gbc_txtAssignmentTac.gridx = 1;
        gbc_txtAssignmentTac.gridy = 4;
        pnlStats.add(txtAssignmentTac, gbc_txtAssignmentTac);
        txtAssignmentTac.setColumns(4);

        txtAssignmentSci = new JFormattedTextField(intFormat);
        txtAssignmentSci.addFocusListener(this);
        txtAssignmentSci.setText("0");
        GridBagConstraints gbc_txtAssignmentSci = new GridBagConstraints();
        gbc_txtAssignmentSci.weightx = 1.0;
        gbc_txtAssignmentSci.insets = new Insets(0, 0, 5, 5);
        gbc_txtAssignmentSci.gridx = 1;
        gbc_txtAssignmentSci.gridy = 5;
        pnlStats.add(txtAssignmentSci, gbc_txtAssignmentSci);
        txtAssignmentSci.setColumns(4);

        txtEventEng = new JFormattedTextField(intFormat);
        txtEventEng.addFocusListener(this);
        txtEventEng.setText("0");
        GridBagConstraints gbc_txtEventEng = new GridBagConstraints();
        gbc_txtEventEng.weightx = 1.0;
        gbc_txtEventEng.insets = new Insets(0, 0, 5, 5);
        gbc_txtEventEng.gridx = 2;
        gbc_txtEventEng.gridy = 3;
        pnlStats.add(txtEventEng, gbc_txtEventEng);
        txtEventEng.setColumns(4);

        txtEventTac = new JFormattedTextField(intFormat);
        txtEventTac.addFocusListener(this);
        txtEventTac.setText("0");
        GridBagConstraints gbc_txtEventTac = new GridBagConstraints();
        gbc_txtEventTac.weightx = 1.0;
        gbc_txtEventTac.insets = new Insets(0, 0, 5, 5);
        gbc_txtEventTac.gridx = 2;
        gbc_txtEventTac.gridy = 4;
        pnlStats.add(txtEventTac, gbc_txtEventTac);
        txtEventTac.setColumns(4);

        txtEventSci = new JFormattedTextField(intFormat);
        txtEventSci.addFocusListener(this);
        txtEventSci.setText("0");
        GridBagConstraints gbc_txtEventSci = new GridBagConstraints();
        gbc_txtEventSci.weightx = 1.0;
        gbc_txtEventSci.insets = new Insets(0, 0, 5, 5);
        gbc_txtEventSci.gridx = 2;
        gbc_txtEventSci.gridy = 5;
        pnlStats.add(txtEventSci, gbc_txtEventSci);
        txtEventSci.setColumns(4);

        txtEventCritRating = new JFormattedTextField(intFormat);
        txtEventCritRating.addFocusListener(this);
        txtEventCritRating.setText("0");
        GridBagConstraints gbc_txtEventCritRating = new GridBagConstraints();
        gbc_txtEventCritRating.weightx = 1.0;
        gbc_txtEventCritRating.insets = new Insets(0, 0, 5, 5);
        gbc_txtEventCritRating.gridx = 2;
        gbc_txtEventCritRating.gridy = 6;
        pnlStats.add(txtEventCritRating, gbc_txtEventCritRating);
        txtEventCritRating.setColumns(4);

        panel_1 = new JPanel();
        panel_1.setBorder(null);
        GridBagConstraints gbc_panel_1 = new GridBagConstraints();
        gbc_panel_1.insets = new Insets(0, 0, 5, 0);
        gbc_panel_1.weighty = 100.0;
        gbc_panel_1.gridwidth = 4;
        gbc_panel_1.fill = GridBagConstraints.BOTH;
        gbc_panel_1.gridx = 0;
        gbc_panel_1.gridy = 7;
        pnlStats.add(panel_1, gbc_panel_1);

        lblScore = new JLabel("Score");
        panel_1.add(lblScore);

        if (Beans.isDesignTime()) {
            initDesignTime();
        } else {
            initRunTime();
        }
    }

    protected void initDesignTime() {

    }

    protected void initRunTime() {
        lblScore.setVisible(Globals.DEBUG);

        cbxAssignment.addItem(AdmAssignment.ASSIGNMENT_NONE);
        for (AdmAssignment assignment : gameData.assignments()) {
            cbxAssignment.addItem(assignment);
        }

        cbxEvent.addItem(Event.EVENT_NONE);
        for (Event event : gameData.events()) {
            cbxEvent.addItem(event);
        }

        cbxAssignment.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!acceptsAssignmentIntent()) {
                    return;
                }
                AdmAssignment selected = (AdmAssignment) cbxAssignment.getSelectedItem();
                int eng = selected == null ? 0 : selected.getEng();
                int tac = selected == null ? 0 : selected.getTac();
                int sci = selected == null ? 0 : selected.getSci();
                int duration = selected == null ? 0 : selected.getHours() * 60 + selected.getMinutes();
                reportAssignmentIntent(assignmentView.withRequirements(eng, tac, sci, duration));
            }
        });

        cbxEvent.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!acceptsAssignmentIntent()) {
                    return;
                }
                Event selected = (Event) cbxEvent.getSelectedItem();
                int eng = selected == null ? 0 : selected.getEng();
                int tac = selected == null ? 0 : selected.getTac();
                int sci = selected == null ? 0 : selected.getSci();
                int critRate = selected == null ? 0 : selected.getCritRate();
                reportAssignmentIntent(assignmentView.withEvent(eng, tac, sci, critRate));
            }
        });
        txtAssignmentEng.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if (!acceptsAssignmentIntent()) {
                    return;
                }
                int value = ((Number) txtAssignmentEng.getValue()).intValue();
                reportAssignmentIntent(assignmentView.withRequiredEng(value));
            }
        });
        txtEventEng.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if (!acceptsAssignmentIntent()) {
                    return;
                }
                int value = ((Number) txtEventEng.getValue()).intValue();
                reportAssignmentIntent(assignmentView.withEventEng(value));
            }
        });
        txtAssignmentTac.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if (!acceptsAssignmentIntent()) {
                    return;
                }
                int value = ((Number) txtAssignmentTac.getValue()).intValue();
                reportAssignmentIntent(assignmentView.withRequiredTac(value));
            }
        });
        txtEventTac.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if (!acceptsAssignmentIntent()) {
                    return;
                }
                int value = ((Number) txtEventTac.getValue()).intValue();
                reportAssignmentIntent(assignmentView.withEventTac(value));
            }
        });
        txtAssignmentSci.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if (!acceptsAssignmentIntent()) {
                    return;
                }
                int value = ((Number) txtAssignmentSci.getValue()).intValue();
                reportAssignmentIntent(assignmentView.withRequiredSci(value));
            }
        });
        txtEventSci.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if (!acceptsAssignmentIntent()) {
                    return;
                }
                int value = ((Number) txtEventSci.getValue()).intValue();
                reportAssignmentIntent(assignmentView.withEventSci(value));
            }
        });
        txtEventCritRating.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if (!acceptsAssignmentIntent()) {
                    return;
                }
                int value = ((Number) txtEventCritRating.getValue()).intValue();
                reportAssignmentIntent(assignmentView.withEventCritRate(value));
            }
        });
        sliTargetCritChance.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (!acceptsAssignmentIntent() || sliTargetCritChance.getValueIsAdjusting())
                    return;
                int value = sliTargetCritChance.getValue();
                reportAssignmentIntent(assignmentView.withTargetCritChance(value));
            }

        });
        clearSolutions();
    }

    public Assignment getAssignment() {
        return assignment;
    }

    /**
     * Binds this editor to one supplied Assignment, or releases its model listener
     * for legacy callers. Replacement workspaces use {@link #setAssignmentView}
     * so user intent returns through their root instead of binding the model here.
     *
     * @param assignment Assignment to render and edit, or {@code null} to unbind
     * @throws IllegalStateException if called outside the Swing event thread
     */
    public void setAssignment(Assignment assignment) {
        Swing.requireEventDispatchThread("bind an Assignment editor");
        if (this.assignment != null) {
            this.assignment.removePropertyChangeListener(this);
        }
        this.assignment = assignment;
        if (this.assignment == null) {
            assignmentView = null;
            assignmentIntent = NO_ASSIGNMENT_INTENT;
            solution = null;
            return;
        }
        this.assignment.addPropertyChangeListener(this);
        projectAssignmentView(AssignmentView.from(assignment), assignment::apply);
    }

    /**
     * Returns whether this editor currently renders an immutable Assignment view.
     */
    public boolean hasAssignmentView() {
        return assignmentView != null;
    }

    /**
     * Renders one immutable Assignment projection and reports later edits through
     * the supplied intent callback without retaining or subscribing to Assignment.
     * Passing {@code null} unbinds the editor while preserving its frozen controls.
     *
     * @param view   immutable Assignment state, or {@code null} to unbind
     * @param intent root-owned callback receiving complete intended state
     * @throws NullPointerException  if {@code view} is non-null and {@code intent} is null
     * @throws IllegalStateException if called outside the Swing event thread
     */
    public void setAssignmentView(AssignmentView view, Consumer<AssignmentView> intent) {
        Swing.requireEventDispatchThread("project an Assignment view");
        if (assignment != null) {
            assignment.removePropertyChangeListener(this);
            assignment = null;
        }
        if (view == null) {
            assignmentView = null;
            assignmentIntent = NO_ASSIGNMENT_INTENT;
            solution = null;
            return;
        }
        projectAssignmentView(view, Objects.requireNonNull(intent, "intent"));
    }

    /**
     * Returns whether a bound projection can publish user intent right now.
     *
     * @throws IllegalStateException if called outside the Swing event thread
     */
    private boolean acceptsAssignmentIntent() {
        Swing.requireEventDispatchThread("handle Assignment interaction");
        // Disposal unbinds before recursively disabling controls; Swing may publish
        // control property events during that disable walk, which must stay local.
        return assignmentView != null && !projectingAssignment;
    }

    /**
     * Publishes one complete intended Assignment state to the current owner.
     *
     * @param intendedView complete immutable user-intended state
     * @throws NullPointerException  if {@code intendedView} is {@code null}
     * @throws IllegalStateException if called outside the Swing event thread
     */
    private void reportAssignmentIntent(AssignmentView intendedView) {
        Swing.requireEventDispatchThread("report Assignment interaction");
        assignmentIntent.accept(Objects.requireNonNull(intendedView, "intendedView"));
    }

    /**
     * Applies one immutable view to Swing controls without echoing user intent.
     *
     * @param view   immutable state to render
     * @param intent callback that owns later user edits
     * @throws NullPointerException if an argument is {@code null}
     */
    private void projectAssignmentView(AssignmentView view, Consumer<AssignmentView> intent) {
        assignmentView = Objects.requireNonNull(view, "view");
        assignmentIntent = Objects.requireNonNull(intent, "intent");
        projectingAssignment = true;
        try {
            txtAssignmentEng.setValue(view.requiredEng());
            txtAssignmentTac.setValue(view.requiredTac());
            txtAssignmentSci.setValue(view.requiredSci());
            txtEventEng.setValue(view.eventEng());
            txtEventTac.setValue(view.eventTac());
            txtEventSci.setValue(view.eventSci());
            txtEventCritRating.setValue(view.eventCritRate());
            sliTargetCritChance.setValue(view.targetCritChance());
        } finally {
            // Projection events are intentionally suppressed only for this synchronous walk.
            projectingAssignment = false;
        }
    }

    /**
     * Presents one calculated Assignment solution from its exact immutable
     * Roster-card identities.
     *
     * @param solution calculated solution, or null to clear the assigned-card
     *                 presentation
     * @throws IllegalStateException if no Assignment view is bound or the call is off the Swing event thread
     */
    public void setAssignmentSolution(AssignmentSolution solution) {
        Swing.requireEventDispatchThread("project an Assignment Solution");
        if (assignmentView == null) {
            throw new IllegalStateException("Cannot project a Solution without an Assignment view");
        }
        if (solution == null) {
            this.solution = null;
            int aEng = assignmentView.eng();
            int aTac = assignmentView.tac();
            int aSci = assignmentView.sci();
            int aCrit = assignmentView.critRate();
            int eventCrit = assignmentView.eventCritRate();
            String cEng = 0 >= aEng ? ColorPositive : ColorNegative;
            String cTac = 0 >= aTac ? ColorPositive : ColorNegative;
            String cSci = 0 >= aSci ? ColorPositive : ColorNegative;
            String cCrit = 0 >= aCrit ? ColorPositive : ColorNegative;
            lblSlottedEng.setText(String.format(HtmlSlot, LabelENG, cEng, 0, aEng));
            lblSlottedTac.setText(String.format(HtmlSlot, LabelTAC, cTac, 0, aTac));
            lblSlottedSci.setText(String.format(HtmlSlot, LabelSCI, cSci, 0, aSci));
            lblSlottedCritRating.setText(String.format(HtmlSlot, labelCRIT, cCrit, eventCrit, aCrit));
            lblScore.setText("0");
            setShip1(null);
            setShip2(null);
            setShip3(null);
        } else {
            this.solution = solution;
            int aEng = assignmentView.eng();
            int aTac = assignmentView.tac();
            int aSci = assignmentView.sci();
            int aCrit = assignmentView.critRate();
            int sEng = solution.getEng();
            int sTac = solution.getTac();
            int sSci = solution.getSci();
            int sCrit = solution.getCritRate();
            String cEng = sEng >= aEng ? ColorPositive : ColorNegative;
            String cTac = sTac >= aTac ? ColorPositive : ColorNegative;
            String cSci = sSci >= aSci ? ColorPositive : ColorNegative;
            String cCrit = sCrit >= aCrit ? ColorPositive : ColorNegative;
            lblSlottedEng.setText(String.format(HtmlSlot, LabelENG, cEng, sEng, aEng));
            lblSlottedTac.setText(String.format(HtmlSlot, LabelTAC, cTac, sTac, aTac));
            lblSlottedSci.setText(String.format(HtmlSlot, LabelSCI, cSci, sSci, aSci));
            lblSlottedCritRating.setText(String.format(HtmlSlot, labelCRIT, cCrit, sCrit, aCrit));
            lblScore.setText("" + solution.getScore());
            RosterCard[] bestCards = solution.getRosterCards();
            pnlShip1.setRosterCard(bestCards[0]);
            pnlShip2.setRosterCard(bestCards[1]);
            pnlShip3.setRosterCard(bestCards[2]);
        }
    }

    public void setShip1(Ship ship) {
        pnlShip1.setShip(ship);
    }

    public void setShip2(Ship ship) {
        pnlShip2.setShip(ship);
    }

    public void setShip3(Ship ship) {
        pnlShip3.setShip(ship);
    }

    /**
     * Reports a complete zero-valued Assignment through the current intent owner and
     * clears displayed Solutions.
     *
     * @throws IllegalStateException if called without a bound view or off the Swing event thread
     */
    public void clearAssignment() {
        Swing.requireEventDispatchThread("clear an Assignment");
        if (assignmentView == null) {
            throw new IllegalStateException("Cannot clear an unbound Assignment editor");
        }
        reportAssignmentIntent(new AssignmentView(0, 0, 0, 0, 0, 0, 0, 0, 0));
        clearSolutions();
    }

    public void clearSolutions() {
        solution = null;
        clearShips();
    }

    public void clearShips() {
        pnlShip1.setShip(null);
        pnlShip2.setShip(null);
        pnlShip3.setShip(null);
    }

    /**
     * Reprojects a legacy directly bound Assignment after one observable field
     * change.
     *
     * @param evt Assignment property change event
     * @throws IllegalStateException if invoked outside the Swing event thread
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        Swing.requireEventDispatchThread("project an Assignment change");
        if (assignment != null) {
            projectAssignmentView(AssignmentView.from(assignment), assignment::apply);
            setAssignmentSolution(solution);
        }
    }

    @Override
    public void focusGained(FocusEvent e) {
        Object source = e.getSource();
        if (source == null)
            return;
        if (source instanceof JFormattedTextField field) {
            SwingUtilities.invokeLater(new SelectAllText(field));
        }
    }

    @Override
    public void focusLost(FocusEvent e) {
    }

    class SelectAllText implements Runnable {
        JFormattedTextField field;

        SelectAllText(JFormattedTextField field) {
            this.field = field;
        }

        @Override
        public void run() {
            field.selectAll();
        }

    }

}
