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
package com.kor.admiralty.ui.panels;

import java.beans.Beans;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.JPanel;

import com.kor.admiralty.Globals;
import com.kor.admiralty.beans.AssignmentView;
import com.kor.admiralty.beans.AssignmentSolution;
import com.kor.admiralty.beans.CompositeSolution;
import com.kor.admiralty.beans.DeploymentOutcome;
import com.kor.admiralty.beans.RosterView;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.AssignmentPanel;
import com.kor.admiralty.ui.DeploymentMessageFormatter;
import com.kor.admiralty.ui.resources.Images;
import com.kor.admiralty.ui.resources.ShipIconFactory;
import com.kor.admiralty.ui.resources.Swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;

import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JButton;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;

import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import static com.kor.admiralty.ui.resources.Strings.Empty;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.*;

public class AssignmentSelectionPanel extends JPanel {

    @Serial
    private static final long serialVersionUID = -3837967504802185087L;
    private final ButtonGroup buttonGroup = new ButtonGroup();
    private final Action actionPlanAssignments = new PlanAssignmentAction();
    private final Action actionClearAssignments = new ClearAssignmentsAction();
    private final Action actionPrevSolution = new PrevSolutionAction();
    private final Action actionBestSolution = new BestSolutionAction();
    private final Action actionNextSolution = new NextSolutionAction();
    private final Action actionDeployShips = new DeployShipsAction();
    private final GameData gameData;
    private final ShipIconFactory iconRenderer;
    private final Actions actions;
    private final MessageDialog messageDialog;
    protected RosterView rosterView;
    protected List<AssignmentView> assignmentViews = List.of();
    protected List<CompositeSolution> solutions = new ArrayList<CompositeSolution>();
    protected int solutionIndex = -1;
    protected JPanel pnlAssignmentButtons;
    protected JScrollPane sclAssignments;
    protected JPanel pnlAssignmentGrid;
    protected AssignmentPanel[] pnlAssignments;
    protected JButton btnPrev;
    protected JButton btnBest;
    protected JButton btnNext;

    /**
     * Creates Assignment planning with explicit lookup, Ship artwork, and root-owned
     * intent dependencies.
     *
     * @param gameData     reference data used by Assignment and Event lookup
     * @param iconRenderer renderer used by Ship cards in displayed Solutions
     * @param actions      root-owned boundary for planning and deployment intent
     * @throws NullPointerException if a dependency is {@code null}
     */
    AssignmentSelectionPanel(GameData gameData, ShipIconFactory iconRenderer, Actions actions) {
        this(gameData, iconRenderer, actions, MessageDialog.swing());
    }

    /**
     * Creates Assignment planning with a narrow message boundary for root
     * integration tests.
     *
     * @param gameData      reference data used by Assignment and Event lookup
     * @param iconRenderer  renderer used by Ship cards in displayed Solutions
     * @param actions       root-owned boundary for planning and deployment intent
     * @param messageDialog deployment and validation message boundary
     * @throws NullPointerException if a dependency is {@code null}
     */
    AssignmentSelectionPanel(
            GameData gameData,
            ShipIconFactory iconRenderer,
            Actions actions,
            MessageDialog messageDialog) {
        this.gameData = Objects.requireNonNull(gameData, "gameData");
        this.iconRenderer = Objects.requireNonNull(iconRenderer, "iconRenderer");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.messageDialog = Objects.requireNonNull(messageDialog, "messageDialog");
        setLayout(new BorderLayout(0, 0));

        JPanel pnlTop = new JPanel();
        add(pnlTop, BorderLayout.NORTH);
        GridBagLayout gbl_pnlTop = new GridBagLayout();
        gbl_pnlTop.columnWidths = new int[]{33, 113, 1, 1, 10, 31, 33, 33, 3, 25, 91, 0};
        gbl_pnlTop.rowHeights = new int[]{23, 14, 0};
        gbl_pnlTop.columnWeights = new double[]{0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                Double.MIN_VALUE};
        gbl_pnlTop.rowWeights = new double[]{1.0, 0.0, Double.MIN_VALUE};
        pnlTop.setLayout(gbl_pnlTop);

        JLabel label = new JLabel(LabelNumAssignments);
        GridBagConstraints gbc_label = new GridBagConstraints();
        gbc_label.anchor = GridBagConstraints.SOUTH;
        gbc_label.weighty = 1.0;
        gbc_label.weightx = 3.0;
        gbc_label.insets = new Insets(5, 5, 5, 5);
        gbc_label.gridx = 0;
        gbc_label.gridy = 0;
        pnlTop.add(label, gbc_label);

        JLabel lblSpacer1 = new JLabel(Empty);
        GridBagConstraints gbc_lblSpacer1 = new GridBagConstraints();
        gbc_lblSpacer1.weighty = 2.0;
        gbc_lblSpacer1.weightx = 100.0;
        gbc_lblSpacer1.gridheight = 2;
        gbc_lblSpacer1.insets = new Insets(5, 5, 0, 5);
        gbc_lblSpacer1.gridx = 1;
        gbc_lblSpacer1.gridy = 1;
        pnlTop.add(lblSpacer1, gbc_lblSpacer1);

        JLabel lblSpacer2 = new JLabel(Empty);
        GridBagConstraints gbc_lblSpacer2 = new GridBagConstraints();
        gbc_lblSpacer2.weighty = 2.0;
        gbc_lblSpacer2.weightx = 100.0;
        gbc_lblSpacer2.gridheight = 2;
        gbc_lblSpacer2.insets = new Insets(5, 5, 0, 5);
        gbc_lblSpacer2.gridx = 4;
        gbc_lblSpacer2.gridy = 1;
        pnlTop.add(lblSpacer2, gbc_lblSpacer2);

        pnlAssignmentButtons = new JPanel();
        GridBagConstraints gbc_panel_1 = new GridBagConstraints();
        gbc_panel_1.weighty = 1.0;
        gbc_panel_1.weightx = 3.0;
        gbc_panel_1.insets = new Insets(0, 5, 5, 5);
        gbc_panel_1.fill = GridBagConstraints.BOTH;
        gbc_panel_1.gridx = 0;
        gbc_panel_1.gridy = 1;
        pnlTop.add(pnlAssignmentButtons, gbc_panel_1);

        JButton btnPlanAssignments = new JButton(LabelPlanAssignments);
        GridBagConstraints gbc_btnPlanAssignments = new GridBagConstraints();
        gbc_btnPlanAssignments.weightx = 1.0;
        gbc_btnPlanAssignments.gridheight = 2;
        gbc_btnPlanAssignments.weighty = 2.0;
        gbc_btnPlanAssignments.fill = GridBagConstraints.BOTH;
        gbc_btnPlanAssignments.insets = new Insets(5, 5, 5, 5);
        gbc_btnPlanAssignments.gridx = 2;
        gbc_btnPlanAssignments.gridy = 0;
        pnlTop.add(btnPlanAssignments, gbc_btnPlanAssignments);
        Swing.setFont(btnPlanAssignments, Font.PLAIN, 12);
        btnPlanAssignments.setAction(actionPlanAssignments);

        JButton btnClearAssignments = new JButton(LabelClearAssignments);
        btnClearAssignments.setAction(actionClearAssignments);
        Swing.setFont(btnClearAssignments, Font.PLAIN, 12);
        GridBagConstraints gbc_btnClearAssignments = new GridBagConstraints();
        gbc_btnClearAssignments.weightx = 1.0;
        gbc_btnClearAssignments.gridheight = 2;
        gbc_btnClearAssignments.weighty = 2.0;
        gbc_btnClearAssignments.insets = new Insets(5, 5, 5, 5);
        gbc_btnClearAssignments.fill = GridBagConstraints.BOTH;
        gbc_btnClearAssignments.gridx = 3;
        gbc_btnClearAssignments.gridy = 0;
        pnlTop.add(btnClearAssignments, gbc_btnClearAssignments);

        JLabel lblSelectPlans = new JLabel(LabelDeploymentPlans);
        GridBagConstraints gbc_lblSelectPlans = new GridBagConstraints();
        gbc_lblSelectPlans.anchor = GridBagConstraints.SOUTH;
        gbc_lblSelectPlans.weighty = 1.0;
        gbc_lblSelectPlans.weightx = 3.0;
        gbc_lblSelectPlans.gridwidth = 3;
        gbc_lblSelectPlans.insets = new Insets(5, 5, 5, 5);
        gbc_lblSelectPlans.gridx = 5;
        gbc_lblSelectPlans.gridy = 0;
        pnlTop.add(lblSelectPlans, gbc_lblSelectPlans);

        btnPrev = new JButton(actionPrevSolution);
        btnPrev.setEnabled(false);
        GridBagConstraints gbc_btnPrev = new GridBagConstraints();
        gbc_btnPrev.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnPrev.weighty = 1.0;
        gbc_btnPrev.weightx = 1.0;
        gbc_btnPrev.insets = new Insets(0, 5, 5, 5);
        gbc_btnPrev.gridx = 5;
        gbc_btnPrev.gridy = 1;
        pnlTop.add(btnPrev, gbc_btnPrev);

        btnBest = new JButton(actionBestSolution);
        btnBest.setEnabled(false);
        GridBagConstraints gbc_btnBest = new GridBagConstraints();
        gbc_btnBest.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnBest.weighty = 1.0;
        gbc_btnBest.weightx = 1.0;
        gbc_btnBest.insets = new Insets(0, 0, 5, 0);
        gbc_btnBest.gridx = 6;
        gbc_btnBest.gridy = 1;
        pnlTop.add(btnBest, gbc_btnBest);

        btnNext = new JButton(actionNextSolution);
        btnNext.setEnabled(false);
        GridBagConstraints gbc_btnNext = new GridBagConstraints();
        gbc_btnNext.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnNext.weighty = 1.0;
        gbc_btnNext.weightx = 1.0;
        gbc_btnNext.insets = new Insets(0, 5, 5, 5);
        gbc_btnNext.gridx = 7;
        gbc_btnNext.gridy = 1;
        pnlTop.add(btnNext, gbc_btnNext);

        JButton btnDeployShips = new JButton(actionDeployShips);
        GridBagConstraints gbc_btnDeployShips = new GridBagConstraints();
        gbc_btnDeployShips.weighty = 2.0;
        gbc_btnDeployShips.weightx = 1.0;
        gbc_btnDeployShips.gridheight = 2;
        gbc_btnDeployShips.insets = new Insets(5, 5, 5, 5);
        gbc_btnDeployShips.fill = GridBagConstraints.VERTICAL;
        gbc_btnDeployShips.gridx = 8;
        gbc_btnDeployShips.gridy = 0;
        pnlTop.add(btnDeployShips, gbc_btnDeployShips);

        sclAssignments = new JScrollPane();
        pnlAssignmentGrid = new JPanel();
        sclAssignments.setViewportView(pnlAssignmentGrid);
        pnlAssignmentGrid.setLayout(new GridLayout(0, 1, 5, 5));
        add(sclAssignments, BorderLayout.CENTER);

        if (Beans.isDesignTime()) {
            initDesignTime();
        } else {
            initRunTime();
        }
    }

    protected void initDesignTime() {
        AssignmentPanel assignmentPanel = new AssignmentPanel(gameData, iconRenderer);
        pnlAssignmentGrid.add(assignmentPanel);
    }

    protected void initRunTime() {
        pnlAssignments = new AssignmentPanel[Globals.MAX_ASSIGNMENTS];
        for (int i = 0; i < Globals.MAX_ASSIGNMENTS; i++) {
            // Create toggle button
            JToggleButton toggle = new JToggleButton(new AssignmentNumberAction(i + 1));
            toggle.setSelected(i == 0);
            pnlAssignmentButtons.add(toggle);
            buttonGroup.add(toggle);

            // Instantiate AssignmentPanel
            pnlAssignments[i] = new AssignmentPanel(gameData, iconRenderer);
            pnlAssignments[i].setVisible(i < 1);
            pnlAssignmentGrid.add(pnlAssignments[i]);
        }
        int height = (int) (pnlAssignments[0].getPreferredSize().getHeight() / 20);
        sclAssignments.getVerticalScrollBar().setUnitIncrement(height);
    }

    /**
     * Renders the fixed Admiral's immutable Assignment and Roster state from one
     * root-supplied workspace projection.
     *
     * @param view complete immutable workspace projection
     * @throws IllegalArgumentException if fewer than the supported number are supplied
     * @throws NullPointerException     if {@code view} or an Assignment view is {@code null}
     * @throws IllegalStateException    if called outside the Swing event thread
     */
    void render(AdmiralWorkspaceView view) {
        Swing.requireEventDispatchThread("project Assignment workspace state");
        Objects.requireNonNull(view, "view");
        assignmentViews = view.assignments();
        rosterView = view.roster();
        if (assignmentViews.size() < Globals.MAX_ASSIGNMENTS) {
            throw new IllegalArgumentException("Expected " + Globals.MAX_ASSIGNMENTS + " Assignment slots");
        }
        for (int i = 0; i < Globals.MAX_ASSIGNMENTS; i++) {
            int assignmentIndex = i;
            AssignmentView assignmentView = Objects.requireNonNull(
                    assignmentViews.get(i),
                    "assignments contains null");
            pnlAssignments[i].setAssignmentView(
                    assignmentView,
                    intendedView -> actions.updateAssignment(assignmentIndex, intendedView));
            pnlAssignments[i].setVisible(i < view.assignmentCount());
            pnlAssignments[i].setEnabled(i < view.assignmentCount());
        }
    }

    /**
     * Replaces the navigable Solutions and presents the best one first.
     *
     * @param solutions Admiral-owned Solutions in score order
     */
    public void setSolutions(List<CompositeSolution> solutions) {
        this.solutions.clear();
        this.solutions.addAll(solutions);
        setSolutionIndex(0);
    }

    /**
     * Clears every retained Solution and removes its selected cards from the
     * Assignment presentation.
     */
    public void clearSolutions() {
        this.solutions.clear();
        setSolutionIndex(-1);
    }

    /**
     * Selects one navigable Solution, or clears the visible selection when the
     * index is out of range.
     *
     * @param index zero-based Solution index
     */
    protected void setSolutionIndex(int index) {
        solutionIndex = index;
        boolean hasSolution = solutionIndex >= 0 && solutionIndex < solutions.size();
        btnPrev.setEnabled(hasSolution && solutionIndex > 0);
        btnBest.setEnabled(hasSolution && solutionIndex != 0);
        btnNext.setEnabled(hasSolution && solutionIndex < this.solutions.size() - 1);
        if (hasSolution) {
            CompositeSolution solution = solutions.get(solutionIndex);
            for (int i = 0; i < solution.size(); i++) {
                AssignmentSolution aSolution = solution.getSolution(i);
                pnlAssignments[i].setAssignmentSolution(aSolution);
            }
        } else {
            for (AssignmentPanel assignmentPanel : pnlAssignments) {
                if (assignmentPanel.hasAssignmentView()) {
                    assignmentPanel.setAssignmentSolution(null);
                }
            }
        }
    }

    /**
     * Presents one deployment message at the Swing dialog boundary.
     * The injected boundary keeps production actions executable in a headless test
     * runtime without changing their owner-window behavior.
     *
     * @param message dialog-ready message owned by the UI layer
     */
    protected void showMessageDialog(Object message) {
        messageDialog.show(SwingUtilities.getWindowAncestor(this), message);
    }

    /**
     * Releases any nested legacy model or root-intent binding and clears
     * identity-bearing Solutions during root disposal.
     *
     * @throws IllegalStateException if called outside the Swing event thread
     */
    void dispose() {
        clearSolutions();
        for (AssignmentPanel assignmentPanel : pnlAssignments) {
            assignmentPanel.setAssignment(null);
        }
    }

    /**
     * Receives Assignment and Solution intent without exposing the bound Admiral.
     */
    interface Actions {

        /**
         * Applies one complete immutable user-intended Assignment state.
         */
        void updateAssignment(int assignmentIndex, AssignmentView intendedView);

        /**
         * Selects how many Assignment slots participate in planning.
         */
        void setAssignmentCount(int assignmentCount);

        /**
         * Calculates ordered Solutions for the root's current projection.
         */
        List<CompositeSolution> solveAssignments();

        /**
         * Clears every currently participating Assignment.
         */
        void clearAssignments();

        /**
         * Deploys one identity-bearing Solution through the fixed Admiral.
         */
        DeploymentOutcome deploySolution(CompositeSolution solution);
    }

    /**
     * Presents Assignment and deployment messages at the outer Swing boundary.
     */
    interface MessageDialog {

        /**
         * Returns the production message presenter.
         */
        static MessageDialog swing() {
            return (owner, message) -> JOptionPane.showMessageDialog(owner, message);
        }

        /**
         * Presents one message relative to the workspace's owning window.
         */
        void show(Window owner, Object message);
    }

    private class AssignmentNumberAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = 1132930085427895573L;
        int number;

        public AssignmentNumberAction(int number) {
            this.number = number;
            putValue(NAME, Empty + number);
            putValue(SHORT_DESCRIPTION, DescNumAssignments);
            int mnemonic = '\0';
            if (number == 1)
                mnemonic = KeyEvent.VK_1;
            else if (number == 2)
                mnemonic = KeyEvent.VK_2;
            else if (number == 3)
                mnemonic = KeyEvent.VK_3;
            putValue(MNEMONIC_KEY, mnemonic);
        }

        public void actionPerformed(ActionEvent e) {
            actions.setAssignmentCount(number);
        }
    }

    private class PlanAssignmentAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = 6495337889146948055L;

        public PlanAssignmentAction() {
            putValue(NAME, HtmlPlanAssignments);
            putValue(SHORT_DESCRIPTION, DescPlanAssignments);
            putValue(MNEMONIC_KEY, KeyEvent.VK_P);
        }

        public void actionPerformed(ActionEvent e) {
            List<CompositeSolution> answers = actions.solveAssignments();
            if (answers.isEmpty()) {
                Window window = SwingUtilities.windowForComponent((Component) e.getSource());
                JOptionPane.showMessageDialog(window, MsgNoSolution);
            } else {
                setSolutions(answers);
            }
        }
    }

    private class ClearAssignmentsAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -2911438009333065675L;

        public ClearAssignmentsAction() {
            putValue(NAME, HtmlClearAssignments);
            putValue(SHORT_DESCRIPTION, DescClearAssignments);
            putValue(MNEMONIC_KEY, KeyEvent.VK_C);
        }

        public void actionPerformed(ActionEvent e) {
            actions.clearAssignments();
            // The root synchronously reprojects every cleared Assignment; only local
            // Solution navigation remains to be reset here.
            clearSolutions();
        }
    }

    private class PrevSolutionAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = 6703568394727570745L;

        public PrevSolutionAction() {
            super(LabelPrev, Images.ICON_PREV);
            putValue(SHORT_DESCRIPTION, DescPrev);
            putValue(MNEMONIC_KEY, KeyEvent.VK_COMMA);
        }

        public void actionPerformed(ActionEvent e) {
            setSolutionIndex(solutionIndex - 1);
        }
    }

    private class BestSolutionAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -2375413099526657416L;

        public BestSolutionAction() {
            super(LabelBest, Images.ICON_BEST);
            putValue(SHORT_DESCRIPTION, DescBest);
            putValue(MNEMONIC_KEY, KeyEvent.VK_B);
        }

        public void actionPerformed(ActionEvent e) {
            setSolutionIndex(0);
        }
    }

    private class NextSolutionAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -8503210636249158149L;

        public NextSolutionAction() {
            super(LabelNext, Images.ICON_NEXT);
            putValue(SHORT_DESCRIPTION, DescNext);
            putValue(MNEMONIC_KEY, KeyEvent.VK_PERIOD);
        }

        public void actionPerformed(ActionEvent e) {
            setSolutionIndex(solutionIndex + 1);
        }
    }

    private class DeployShipsAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -4737708180950586981L;

        public DeployShipsAction() {
            putValue(NAME, HtmlDeployShips);
            putValue(SHORT_DESCRIPTION, DescDeployShips);
            putValue(MNEMONIC_KEY, KeyEvent.VK_D);
        }

        /**
         * Deploys the selected identity-bearing Solution through one atomic Admiral
         * transaction.
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            if (solutionIndex < 0) {
                showMessageDialog(MsgNoShipsToDeploy);
                return;
            }

            CompositeSolution solution = solutions.get(solutionIndex);
            if (solution.getRosterCards().isEmpty()) {
                showMessageDialog(MsgNoShipsToDeploy);
                return;
            }

            DeploymentOutcome outcome = actions.deploySolution(solution);
            String message = DeploymentMessageFormatter.format(outcome);
            showMessageDialog(message);
        }
    }
}
