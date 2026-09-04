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

import javax.swing.JPanel;
import java.awt.GridBagLayout;
import javax.swing.JLabel;
import javax.swing.JList;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

import javax.swing.JButton;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterState;
import com.kor.admiralty.beans.RosterView;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.PlayerFaction;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.RosterCardSelections;
import com.kor.admiralty.ui.models.RosterCardListModel;
import com.kor.admiralty.ui.renderers.RosterCardCellRenderer;
import com.kor.admiralty.ui.resources.Images;
import com.kor.admiralty.ui.resources.ShipIconFactory;
import com.kor.admiralty.ui.resources.Swing;
import com.kor.admiralty.ui.util.TextFileFilter;

import static com.kor.admiralty.ui.resources.Strings.Empty;
import static com.kor.admiralty.ui.resources.Strings.AdmiralPanel.*;

public class ShipRosterPanel extends JPanel {

    @Serial
    private static final long serialVersionUID = -6255733882357549115L;
    private final Action actionAllMaintenanceToActive = new AllMaintenanceToActiveAction();
    private final Action actionAllActiveToMaintenance = new AllActiveToMaintenanceAction();
    private final Action actionMaintenanceToActive = new MaintenanceToActiveAction();
    private final Action actionActiveToMaintenance = new ActiveToMaintenanceAction();
    private final Action actionAddShip = new AddActiveShipAction();
    private final Action actionRemoveShip = new RemoveActiveShipAction();
    private final Action actionExportShips = new ExportShipsAction();
    private final Action actionImportShips = new ImportShipsAction();
    private final GameData gameData;
    private final Path dataDirectory;
    private final ShipIconFactory iconRenderer;
    private final RosterFileDialog rosterFileDialog;
    private final RosterSelectionDialog rosterSelectionDialog;
    private final Actions actions;
    protected String admiralName;
    protected PlayerFaction faction;
    protected RosterView rosterView;
    protected RosterCardListModel modelActive;
    protected RosterCardListModel modelMaintenance;
    protected JLabel lblActive;
    protected JLabel lblMaintenance;
    protected JList<RosterCard> lstActive;
    protected JList<RosterCard> lstMaintenance;

    /**
     * Creates reusable-Roster presentation with explicit lookup, artwork, and user
     * intent dependencies.
     *
     * @param gameData      reference data used by reusable Ship selection and import
     * @param dataDirectory resolved application data directory used by file choosers
     * @param iconRenderer  renderer used by lists and selection dialogs
     * @param actions       root-owned boundary for Roster user intent
     * @throws NullPointerException if any dependency is {@code null}
     */
    ShipRosterPanel(
            GameData gameData,
            Path dataDirectory,
            ShipIconFactory iconRenderer,
            Actions actions) {
        this(
                gameData,
                dataDirectory,
                iconRenderer,
                RosterFileDialog.swing(),
                actions);
    }

    /**
     * Creates reusable-Roster presentation with a narrow file-dialog boundary for
     * headless root integration tests.
     *
     * @param gameData         reference data used by reusable Ship selection and import
     * @param dataDirectory    resolved application data directory used by file choosers
     * @param iconRenderer     renderer used by lists and selection dialogs
     * @param rosterFileDialog file selection and outcome-presentation boundary
     * @param actions          root-owned boundary for Roster user intent
     * @throws NullPointerException if any dependency is {@code null}
     */
    ShipRosterPanel(
            GameData gameData,
            Path dataDirectory,
            ShipIconFactory iconRenderer,
            RosterFileDialog rosterFileDialog,
            Actions actions) {
        this(
                gameData,
                dataDirectory,
                iconRenderer,
                rosterFileDialog,
                RosterSelectionDialog.swing(),
                actions);
    }

    /**
     * Creates reusable-Roster presentation with supplied file and Ship-selection
     * adapters for root integration tests.
     *
     * @param gameData              reference data used by reusable Ship selection and import
     * @param dataDirectory         resolved application data directory used by file choosers
     * @param iconRenderer          renderer used by lists and selection dialogs
     * @param rosterFileDialog      file selection and outcome-presentation boundary
     * @param rosterSelectionDialog reusable Ship add/remove selection seam
     * @param actions               root-owned boundary for Roster user intent
     * @throws NullPointerException if any dependency is {@code null}
     */
    ShipRosterPanel(
            GameData gameData,
            Path dataDirectory,
            ShipIconFactory iconRenderer,
            RosterFileDialog rosterFileDialog,
            RosterSelectionDialog rosterSelectionDialog,
            Actions actions) {
        this.gameData = Objects.requireNonNull(gameData, "gameData");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.iconRenderer = Objects.requireNonNull(iconRenderer, "iconRenderer");
        this.rosterFileDialog = Objects.requireNonNull(rosterFileDialog, "rosterFileDialog");
        this.rosterSelectionDialog = Objects.requireNonNull(rosterSelectionDialog, "rosterSelectionDialog");
        this.actions = Objects.requireNonNull(actions, "actions");
        GridBagLayout gbl_panel = new GridBagLayout();
        gbl_panel.columnWidths = new int[]{0, 0, 0, 0, 0};
        gbl_panel.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
        gbl_panel.columnWeights = new double[]{1.0, 1.0, 0.0, 0.0, Double.MIN_VALUE};
        gbl_panel.rowWeights = new double[]{0.0, 1.0, 1.0, 1.0, 0.0, 0.0, 1.0, Double.MIN_VALUE};
        setLayout(gbl_panel);

        lblActive = new JLabel(LabelActiveShips);
        GridBagConstraints gbc_lblActive = new GridBagConstraints();
        gbc_lblActive.fill = GridBagConstraints.HORIZONTAL;
        gbc_lblActive.anchor = GridBagConstraints.WEST;
        gbc_lblActive.insets = new Insets(5, 5, 5, 5);
        gbc_lblActive.gridx = 0;
        gbc_lblActive.gridy = 0;
        add(lblActive, gbc_lblActive);

        lblMaintenance = new JLabel(LabelMaintenanceShips);
        GridBagConstraints gbc_lblMaintenance = new GridBagConstraints();
        gbc_lblMaintenance.fill = GridBagConstraints.HORIZONTAL;
        gbc_lblMaintenance.anchor = GridBagConstraints.WEST;
        gbc_lblMaintenance.insets = new Insets(5, 5, 5, 5);
        gbc_lblMaintenance.gridx = 2;
        gbc_lblMaintenance.gridy = 0;
        add(lblMaintenance, gbc_lblMaintenance);

        JScrollPane sclActive = new JScrollPane();
        RosterScrolling.configureReusableCards(sclActive);
        GridBagConstraints gbc_sclActive = new GridBagConstraints();
        gbc_sclActive.weighty = 10.0;
        gbc_sclActive.weightx = 100.0;
        gbc_sclActive.fill = GridBagConstraints.BOTH;
        gbc_sclActive.gridheight = 6;
        gbc_sclActive.insets = new Insets(5, 5, 5, 5);
        gbc_sclActive.gridx = 0;
        gbc_sclActive.gridy = 1;
        add(sclActive, gbc_sclActive);

        modelActive = new RosterCardListModel();
        lstActive = new JList<RosterCard>(modelActive);
        lstActive.addMouseListener(new MouseAdapter() {
            /**
             * Moves a double-clicked Active card to Maintenance in one Admiral operation.
             */
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = lstActive.locationToIndex(e.getPoint());
                    RosterCard card = modelActive.getElementAt(index);
                    lstActive.clearSelection();
                    actions.moveReusableCards(List.of(card), RosterState.MAINTENANCE);
                }
            }
        });
        lstActive.setCellRenderer(RosterCardCellRenderer.shipCards(iconRenderer));
        sclActive.setViewportView(lstActive);

        JLabel lblTop = new JLabel("");
        GridBagConstraints gbc_lblTop = new GridBagConstraints();
        gbc_lblTop.weighty = 100.0;
        gbc_lblTop.weightx = 1.0;
        gbc_lblTop.insets = new Insets(0, 0, 5, 5);
        gbc_lblTop.gridx = 1;
        gbc_lblTop.gridy = 1;
        add(lblTop, gbc_lblTop);

        JScrollPane sclMaintenance = new JScrollPane();
        RosterScrolling.configureReusableCards(sclMaintenance);
        GridBagConstraints gbc_sclMaintenance = new GridBagConstraints();
        gbc_sclMaintenance.weighty = 10.0;
        gbc_sclMaintenance.weightx = 100.0;
        gbc_sclMaintenance.fill = GridBagConstraints.BOTH;
        gbc_sclMaintenance.gridheight = 6;
        gbc_sclMaintenance.insets = new Insets(5, 5, 5, 5);
        gbc_sclMaintenance.gridx = 2;
        gbc_sclMaintenance.gridy = 1;
        add(sclMaintenance, gbc_sclMaintenance);

        modelMaintenance = new RosterCardListModel();
        lstMaintenance = new JList<RosterCard>(modelMaintenance);
        lstMaintenance.addMouseListener(new MouseAdapter() {
            /**
             * Moves a double-clicked Maintenance card to Active in one Admiral operation.
             */
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = lstMaintenance.locationToIndex(e.getPoint());
                    RosterCard card = modelMaintenance.getElementAt(index);
                    lstMaintenance.clearSelection();
                    actions.moveReusableCards(List.of(card), RosterState.ACTIVE);
                }
            }
        });
        lstMaintenance.setCellRenderer(RosterCardCellRenderer.shipCards(iconRenderer));
        sclMaintenance.setViewportView(lstMaintenance);

        JPanel pnlButtons = new JPanel();
        pnlButtons.setBorder(null);
        GridBagConstraints gbc_pnlButtons = new GridBagConstraints();
        gbc_pnlButtons.weighty = 100.0;
        gbc_pnlButtons.weightx = 1.0;
        gbc_pnlButtons.insets = new Insets(5, 0, 0, 5);
        gbc_pnlButtons.fill = GridBagConstraints.HORIZONTAL;
        gbc_pnlButtons.anchor = GridBagConstraints.NORTH;
        gbc_pnlButtons.gridheight = 6;
        gbc_pnlButtons.gridx = 3;
        gbc_pnlButtons.gridy = 1;
        add(pnlButtons, gbc_pnlButtons);
        GridBagLayout gbl_pnlButtons = new GridBagLayout();
        gbl_pnlButtons.columnWidths = new int[]{0, 0};
        gbl_pnlButtons.rowHeights = new int[]{0, 0, 0, 0, 0};
        gbl_pnlButtons.columnWeights = new double[]{0.0, Double.MIN_VALUE};
        gbl_pnlButtons.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
        pnlButtons.setLayout(gbl_pnlButtons);

        JButton btnAddShip = new JButton(actionAddShip);
        GridBagConstraints gbc_btnAddShip = new GridBagConstraints();
        gbc_btnAddShip.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnAddShip.insets = new Insets(0, 0, 5, 0);
        gbc_btnAddShip.gridx = 0;
        gbc_btnAddShip.gridy = 0;
        pnlButtons.add(btnAddShip, gbc_btnAddShip);

        JButton btnRemoveShip = new JButton(actionRemoveShip);
        GridBagConstraints gbc_btnRemoveShip = new GridBagConstraints();
        gbc_btnRemoveShip.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnRemoveShip.gridx = 0;
        gbc_btnRemoveShip.gridy = 1;
        pnlButtons.add(btnRemoveShip, gbc_btnRemoveShip);

        JButton btnExportShips = new JButton(actionExportShips);
        GridBagConstraints gbc_btnExportShips = new GridBagConstraints();
        gbc_btnExportShips.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnExportShips.insets = new Insets(5, 0, 2, 0);
        gbc_btnExportShips.gridx = 0;
        gbc_btnExportShips.gridy = 2;
        pnlButtons.add(btnExportShips, gbc_btnExportShips);

        JButton btnImportShips = new JButton(actionImportShips);
        GridBagConstraints gbc_btnImportShips = new GridBagConstraints();
        gbc_btnImportShips.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnImportShips.gridx = 0;
        gbc_btnImportShips.gridy = 3;
        pnlButtons.add(btnImportShips, gbc_btnImportShips);

        JButton btnAllActive = new JButton(actionAllMaintenanceToActive);
        GridBagConstraints gbc_btnAllActive = new GridBagConstraints();
        gbc_btnAllActive.weighty = 1.0;
        gbc_btnAllActive.weightx = 1.0;
        gbc_btnAllActive.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnAllActive.insets = new Insets(0, 0, 5, 5);
        gbc_btnAllActive.gridx = 1;
        gbc_btnAllActive.gridy = 2;
        add(btnAllActive, gbc_btnAllActive);

        JButton btnActive = new JButton(actionMaintenanceToActive);
        GridBagConstraints gbc_btnActive = new GridBagConstraints();
        gbc_btnActive.weighty = 1.0;
        gbc_btnActive.weightx = 1.0;
        gbc_btnActive.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnActive.insets = new Insets(0, 0, 5, 5);
        gbc_btnActive.gridx = 1;
        gbc_btnActive.gridy = 3;
        add(btnActive, gbc_btnActive);

        JButton btnMaintenance = new JButton(actionActiveToMaintenance);
        GridBagConstraints gbc_btnMaintenance = new GridBagConstraints();
        gbc_btnMaintenance.weighty = 1.0;
        gbc_btnMaintenance.weightx = 1.0;
        gbc_btnMaintenance.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnMaintenance.insets = new Insets(0, 0, 5, 5);
        gbc_btnMaintenance.gridx = 1;
        gbc_btnMaintenance.gridy = 4;
        add(btnMaintenance, gbc_btnMaintenance);

        JButton btnAllMaintenance = new JButton(actionAllActiveToMaintenance);
        GridBagConstraints gbc_btnAllMaintenance = new GridBagConstraints();
        gbc_btnAllMaintenance.weighty = 1.0;
        gbc_btnAllMaintenance.weightx = 1.0;
        gbc_btnAllMaintenance.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnAllMaintenance.insets = new Insets(0, 0, 5, 5);
        gbc_btnAllMaintenance.gridx = 1;
        gbc_btnAllMaintenance.gridy = 5;
        add(btnAllMaintenance, gbc_btnAllMaintenance);

        JLabel lblBottom = new JLabel("");
        GridBagConstraints gbc_lblBottom = new GridBagConstraints();
        gbc_lblBottom.weighty = 100.0;
        gbc_lblBottom.weightx = 1.0;
        gbc_lblBottom.insets = new Insets(0, 0, 5, 5);
        gbc_lblBottom.gridx = 1;
        gbc_lblBottom.gridy = 6;
        add(lblBottom, gbc_lblBottom);
    }

    /**
     * Applies one root-supplied identity and Roster projection without selecting or
     * subscribing to an Admiral.
     *
     * @param view complete immutable workspace projection
     * @throws NullPointerException  if {@code view} is {@code null}
     * @throws IllegalStateException if called outside the Swing event thread
     */
    void render(AdmiralWorkspaceView view) {
        Swing.requireEventDispatchThread("project reusable Roster state");
        Objects.requireNonNull(view, "view");
        admiralName = view.name();
        faction = view.faction();
        rosterView = view.roster();
        List<RosterCard> activeCards = rosterView.getActiveCards();
        List<RosterCard> maintenanceCards = rosterView.getMaintenanceCards();
        modelActive.setCards(activeCards);
        modelMaintenance.setCards(maintenanceCards);
        lblActive.setText(String.format(HtmlActiveShips, activeCards.size()));
        lblMaintenance.setText(String.format(HtmlMaintenanceShips, maintenanceCards.size()));
    }

    /**
     * Creates the configured export chooser rooted at the supplied data directory.
     *
     * @return chooser suggesting this fixed Admiral's conventional text filename
     */
    private JFileChooser createExportFileChooser() {
        JFileChooser fileChooser = createRosterFileChooser(TitleExportShips, JFileChooser.SAVE_DIALOG);
        fileChooser.setSelectedFile(dataDirectory.resolve(admiralName + ".txt").toFile());
        return fileChooser;
    }

    /**
     * Creates the configured import chooser rooted at the supplied data directory.
     *
     * @return chooser suggesting the conventional file when it exists
     */
    private JFileChooser createImportFileChooser() {
        JFileChooser fileChooser = createRosterFileChooser(TitleImportShips, JFileChooser.OPEN_DIALOG);
        Path conventionalFile = dataDirectory.resolve(admiralName + ".txt");
        if (Files.isRegularFile(conventionalFile)) {
            fileChooser.setSelectedFile(conventionalFile.toFile());
        }
        return fileChooser;
    }

    /**
     * Configures one text-file chooser without consulting application-global state.
     *
     * @param title      established user-facing dialog title
     * @param dialogType {@link JFileChooser#SAVE_DIALOG} or {@link JFileChooser#OPEN_DIALOG}
     * @return chooser rooted at the supplied resolved data directory
     */
    private JFileChooser createRosterFileChooser(String title, int dialogType) {
        JFileChooser fileChooser = new JFileChooser(dataDirectory.toFile());
        fileChooser.setDialogTitle(title);
        fileChooser.setDialogType(dialogType);
        fileChooser.setFileFilter(TextFileFilter.SINGLETON);
        return fileChooser;
    }

    /**
     * Exports this Admiral's reusable Active and Maintenance Ship names in stable
     * legacy order.
     *
     * @param file selected destination file
     * @return structured success or failure presentation
     */
    private RosterFileOutcome exportRoster(File file) {
        TreeSet<Ship> ships = new TreeSet<Ship>(
                RosterCardSelections.ships(rosterView.getReusableCards()));
        String filename = file.getName();
        if (actions.exportShipNames(file, ships)) {
            return new RosterFileOutcome(
                    RosterFileOutcome.Type.SUCCESS,
                    String.format(MsgExportSuccessful, filename),
                    TitleExportShips);
        }
        return new RosterFileOutcome(
                RosterFileOutcome.Type.FAILURE,
                String.format(MsgExportFailed, filename),
                TitleExportShips);
    }

    /**
     * Imports known and Renamed Ship names through the supplied GameData into this
     * fixed Admiral's Active Roster.
     *
     * @param file selected source file
     * @return structured success, no-op, or failure presentation
     */
    private RosterFileOutcome importRoster(File file) {
        int importedCount = actions.importShipNames(file);
        String filename = file.getName();
        if (importedCount < 0) {
            return new RosterFileOutcome(
                    RosterFileOutcome.Type.FAILURE,
                    String.format(MsgImportFailed, filename),
                    TitleImportShips);
        }
        if (importedCount == 0) {
            return new RosterFileOutcome(
                    RosterFileOutcome.Type.NO_OP,
                    String.format(MsgNoImport, filename),
                    TitleImportShips);
        }
        return new RosterFileOutcome(
                RosterFileOutcome.Type.SUCCESS,
                String.format(MsgImportSuccessful, importedCount, filename),
                TitleImportShips);
    }

    /**
     * Opens the production Swing chooser and message dialogs.
     */
    private enum SwingRosterFileDialog implements RosterFileDialog {
        INSTANCE;

        /**
         * Returns the approved selected file, or {@code null} after cancellation.
         */
        @Override
        public File chooseFile(Window owner, JFileChooser chooser, String approveLabel) {
            int result = chooser.showDialog(owner, approveLabel);
            return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
        }

        /**
         * Presents the outcome with its established title and severity.
         */
        @Override
        public void showOutcome(Window owner, RosterFileOutcome outcome) {
            JOptionPane.showMessageDialog(
                    owner,
                    outcome.message(),
                    outcome.title(),
                    outcome.messageType());
        }
    }

    /**
     * Receives reusable-Roster and file-transfer intent without exposing Admiral.
     */
    interface Actions {

        /**
         * Moves exact reusable card identities to one destination.
         */
        void moveReusableCards(List<RosterCard> cards, RosterState destination);

        /**
         * Adds canonical reusable Ships to one destination.
         */
        void addReusableShips(List<Ship> ships, RosterState destination);

        /**
         * Removes exact reusable card identities.
         */
        void removeReusableCards(List<RosterCard> cards);

        /**
         * Writes the supplied projected Ship names through the root's store.
         */
        boolean exportShipNames(File file, Collection<Ship> ships);

        /**
         * Imports canonical Ship names into the root's fixed Admiral.
         */
        int importShipNames(File file);
    }

    /**
     * Keeps native file selection and message presentation at the outer Swing
     * boundary while Roster mutation remains local to this panel.
     */
    interface RosterFileDialog {

        /**
         * Returns the production Swing implementation.
         *
         * @return shared native-dialog boundary
         */
        static RosterFileDialog swing() {
            return SwingRosterFileDialog.INSTANCE;
        }

        /**
         * Presents one configured chooser.
         *
         * @param owner        dialog owner derived from Swing ancestry
         * @param chooser      configured Roster text-file chooser
         * @param approveLabel established approve-button label
         * @return selected file, or {@code null} when the user cancels
         */
        File chooseFile(Window owner, JFileChooser chooser, String approveLabel);

        /**
         * Presents one structured Roster file-operation outcome.
         *
         * @param owner   dialog owner derived from Swing ancestry
         * @param outcome success, no-op, or failure presentation
         */
        void showOutcome(Window owner, RosterFileOutcome outcome);
    }

    /**
     * Captures one file-operation meaning and its established dialog presentation.
     */
    record RosterFileOutcome(Type type, String message, String title) {

        /**
         * Validates complete outcome presentation at construction.
         *
         * @throws NullPointerException if {@code type}, {@code message}, or
         *                              {@code title} is {@code null}
         */
        RosterFileOutcome {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(title, "title");
        }

        /**
         * Returns the Swing severity implied by this semantic outcome.
         *
         * @return one of the {@link JOptionPane} message constants
         */
        int messageType() {
            return type.messageType();
        }

        /**
         * Established semantic categories shared by import and export.
         */
        enum Type {
            SUCCESS(JOptionPane.INFORMATION_MESSAGE),
            NO_OP(JOptionPane.INFORMATION_MESSAGE),
            FAILURE(JOptionPane.ERROR_MESSAGE);

            private final int messageType;

            /**
             * Captures the Swing severity implied by this semantic outcome.
             *
             * @param messageType one of the {@link JOptionPane} message constants
             */
            Type(int messageType) {
                this.messageType = messageType;
            }

            /**
             * Returns the established Swing presentation severity.
             *
             * @return one of the {@link JOptionPane} message constants
             */
            int messageType() {
                return messageType;
            }
        }
    }

    private class ExportShipsAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -7749618373959400163L;

        /**
         * Creates the established export action without adding a new mnemonic.
         */
        private ExportShipsAction() {
            super(LabelExportShips, Images.ICON_EXPORT);
            putValue(SHORT_DESCRIPTION, DescExportShips);
        }

        /**
         * Selects a destination and presents the concrete persistence outcome.
         */
        @Override
        public void actionPerformed(ActionEvent event) {
            Window window = SwingUtilities.getWindowAncestor((Component) event.getSource());
            File file = rosterFileDialog.chooseFile(window, createExportFileChooser(), LabelExportShips);
            if (file != null) {
                rosterFileDialog.showOutcome(window, exportRoster(file));
            }
        }
    }

    private class ImportShipsAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = 8306416465068232284L;

        /**
         * Creates the established import action without adding a new mnemonic.
         */
        private ImportShipsAction() {
            super(LabelImportShips, Images.ICON_IMPORT);
            putValue(SHORT_DESCRIPTION, DescImportShips);
        }

        /**
         * Selects a source and presents the canonical import outcome.
         */
        @Override
        public void actionPerformed(ActionEvent event) {
            Window window = SwingUtilities.getWindowAncestor((Component) event.getSource());
            File file = rosterFileDialog.chooseFile(window, createImportFileChooser(), LabelImportShips);
            if (file != null) {
                rosterFileDialog.showOutcome(window, importRoster(file));
            }
        }
    }

    private class AllMaintenanceToActiveAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -509981822658289573L;

        public AllMaintenanceToActiveAction() {
            super(Empty, Images.ICON_LEFT_ALL);
            putValue(SHORT_DESCRIPTION, DescAllMaintenanceToActive);
        }

        /**
         * Moves every Maintenance card to Active in one Admiral operation.
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            List<RosterCard> cards = rosterView.getMaintenanceCards();
            if (!cards.isEmpty()) {
                actions.moveReusableCards(cards, RosterState.ACTIVE);
            }
        }
    }

    private class AllActiveToMaintenanceAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -7137558996349465891L;

        public AllActiveToMaintenanceAction() {
            super(Empty, Images.ICON_RIGHT_ALL);
            putValue(SHORT_DESCRIPTION, DescAllActiveToMaintenance);
        }

        /**
         * Moves every Active card to Maintenance in one Admiral operation.
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            List<RosterCard> cards = rosterView.getActiveCards();
            if (!cards.isEmpty()) {
                actions.moveReusableCards(cards, RosterState.MAINTENANCE);
            }
        }
    }

    private class MaintenanceToActiveAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = 9205828368701721872L;

        public MaintenanceToActiveAction() {
            super(Empty, Images.ICON_LEFT_ONE);
            putValue(SHORT_DESCRIPTION, DescMaintenanceToActive);
        }

        /**
         * Moves the selected Maintenance cards to Active in one Admiral operation.
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            List<RosterCard> cards = lstMaintenance.getSelectedValuesList();
            if (!cards.isEmpty()) {
                actions.moveReusableCards(cards, RosterState.ACTIVE);
            }
            lstActive.setSelectedIndices(new int[0]);
            lstMaintenance.setSelectedIndices(new int[0]);
        }
    }

    private class ActiveToMaintenanceAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -3757003998462960644L;

        public ActiveToMaintenanceAction() {
            super(Empty, Images.ICON_RIGHT_ONE);
            putValue(SHORT_DESCRIPTION, DescActiveToMaintenance);
        }

        /**
         * Moves the selected Active cards to Maintenance in one Admiral operation.
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            List<RosterCard> cards = lstActive.getSelectedValuesList();
            if (!cards.isEmpty()) {
                actions.moveReusableCards(cards, RosterState.MAINTENANCE);
            }
            lstActive.setSelectedIndices(new int[0]);
            lstMaintenance.setSelectedIndices(new int[0]);
        }
    }

    private class AddActiveShipAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = 2156513045423271256L;

        public AddActiveShipAction() {
            super(LabelShip, Images.ICON_ADD);
            putValue(SHORT_DESCRIPTION, DescAddActiveShips);
        }

        /**
         * Adds all selected reusable Ships to Active in one Admiral operation.
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            Window window = SwingUtilities.getWindowAncestor((Component) e.getSource());
            TreeSet<Ship> inputShips = new TreeSet<Ship>(gameData.ships());
            for (RosterCard card : rosterView.getReusableCards()) {
                inputShips.remove(card.getShip());
            }
            List<Ship> ships = rosterSelectionDialog.chooseReusableShips(
                    window,
                    faction,
                    inputShips,
                    iconRenderer);
            if (!ships.isEmpty()) {
                actions.addReusableShips(ships, RosterState.ACTIVE);
            }
        }
    }

    private class RemoveActiveShipAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = -4040927572982355707L;

        public RemoveActiveShipAction() {
            super(LabelShip, Images.ICON_REMOVE);
            putValue(SHORT_DESCRIPTION, DescRemoveActiveShips);
        }

        /**
         * Removes the exact selected reusable cards in one Admiral operation.
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            Window window = SwingUtilities.getWindowAncestor((Component) e.getSource());
            List<RosterCard> selectedCards = rosterSelectionDialog.chooseRosterCards(
                    window,
                    rosterView.getReusableCards(),
                    iconRenderer,
                    TitleRemoveActiveShips);
            if (!selectedCards.isEmpty()) {
                actions.removeReusableCards(selectedCards);
            }
        }
    }

}
