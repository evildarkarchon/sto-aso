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
package com.kor.admiralty.ui.shipfilter;

import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.ui.ShipDetailsPanel;
import com.kor.admiralty.ui.resources.Swing;
import org.jdesktop.swingx.JXTaskPane;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.Serial;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static com.kor.admiralty.ui.resources.Strings.ShipSelectionPanel.*;

/**
 * Reusable Swing presentation of entries governed by one immutable Ship Filter.
 * Construction and mutation are confined to the Swing event-dispatch thread.
 *
 * @param <E> entry type displayed without copying identities
 * @param <O> ordering type paired with the entry type
 */
public final class ShipFilterView<E, O> extends JPanel {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ProjectionListModel<E> model = new ProjectionListModel<E>();
    private final JList<E> entries = new JList<E>(model);
    private final Map<ShipFaction, JCheckBox> factionControls = new EnumMap<ShipFaction, JCheckBox>(ShipFaction.class);
    private final Map<Role, JCheckBox> roleControls = new EnumMap<Role, JCheckBox>(Role.class);
    private final Map<Tier, JCheckBox> tierControls = new EnumMap<Tier, JCheckBox>(Tier.class);
    private final Map<Rarity, JCheckBox> rarityControls = new EnumMap<Rarity, JCheckBox>(Rarity.class);
    private final ShipDetailsPanel details;
    private ShipFilter<E, O> filter;
    private List<E> sourceEntries = List.of();

    /**
     * Creates one typed view and publishes its initial entries through the
     * supplied complete filter.
     *
     * @param filter         complete immutable initial Ship Filter
     * @param initialEntries caller-owned entries to project
     * @param renderer       presentation renderer selected by the named factory
     * @param includeDetails whether to include the Ship details column
     */
    ShipFilterView(
            ShipFilter<E, O> filter,
            Collection<? extends E> initialEntries,
            ListCellRenderer<? super E> renderer,
            boolean includeDetails) {
        Swing.requireEventDispatchThread("construct a Ship Filter view");
        this.filter = java.util.Objects.requireNonNull(filter, "filter");
        entries.setCellRenderer(java.util.Objects.requireNonNull(renderer, "renderer"));
        entries.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        details = includeDetails ? new ShipDetailsPanel() : null;
        if (details != null) {
            entries.addListSelectionListener(event -> updateDetails());
        }

        GridBagLayout layout = new GridBagLayout();
        layout.columnWidths = includeDetails ? new int[]{250, 250} : new int[]{250};
        layout.rowHeights = new int[]{0};
        layout.columnWeights = includeDetails ? new double[]{0.0, 1.0} : new double[]{1.0};
        layout.rowWeights = new double[]{1.0};
        setLayout(layout);

        JPanel entriesPanel = new JPanel(new BorderLayout());
        entriesPanel.add(createFilterPane(), BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(entries);
        scrollPane.getVerticalScrollBar().addAdjustmentListener(event -> {
            if (event.getAdjustmentType() == java.awt.event.AdjustmentEvent.TRACK) {
                event.getAdjustable().setBlockIncrement(1);
            }
        });
        entriesPanel.add(scrollPane, BorderLayout.CENTER);

        GridBagConstraints entriesConstraints = new GridBagConstraints();
        entriesConstraints.weightx = 50.0;
        entriesConstraints.insets = new Insets(5, 5, 5, 5);
        entriesConstraints.fill = GridBagConstraints.BOTH;
        entriesConstraints.gridx = 0;
        entriesConstraints.gridy = 0;
        add(entriesPanel, entriesConstraints);

        if (details != null) {
            GridBagConstraints detailsConstraints = new GridBagConstraints();
            detailsConstraints.weightx = 50.0;
            detailsConstraints.insets = new Insets(5, 5, 0, 0);
            detailsConstraints.fill = GridBagConstraints.BOTH;
            detailsConstraints.gridx = 1;
            detailsConstraints.gridy = 0;
            add(details, detailsConstraints);
        }
        Swing.configureScreenRelativeDialogHeight(this);
        present(initialEntries);
    }

    /**
     * Adds one bold dimension heading at the established grid position.
     *
     * @param controls filter control grid
     * @param label    established heading text
     * @param column   zero-based dimension column
     */
    private static void addHeading(JPanel controls, String label, int column) {
        JLabel heading = new JLabel(label);
        Swing.setFont(heading, Font.BOLD, 12);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.insets = new Insets(5, 5, 5, column == 3 ? 0 : 5);
        constraints.gridx = column;
        constraints.gridy = 0;
        controls.add(heading, constraints);
    }

    /**
     * Captures the checked values for one dimension as an immutable set.
     *
     * @param controls enum values and their checkboxes
     * @param <T>      dimension value type
     * @return immutable selected values
     */
    private static <T> Set<T> selectedValues(Map<T, JCheckBox> controls) {
        return controls.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Tests membership by object identity so equal-but-distinct Roster cards can
     * never inherit one another's selection in later named paths.
     *
     * @param identities previously selected entries
     * @param candidate  projected entry being considered
     * @return whether the exact candidate instance was selected
     */
    private static boolean containsIdentity(List<?> identities, Object candidate) {
        for (Object identity : identities) {
            if (identity == candidate) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds the established collapsed four-dimension control surface from the
     * complete initial immutable filter.
     *
     * @return filter task pane with all 21 controls
     */
    private JXTaskPane createFilterPane() {
        JXTaskPane taskPane = new JXTaskPane(LabelFilter);
        taskPane.setCollapsed(true);
        taskPane.setTitle(TitleFilter);

        JPanel controls = new JPanel();
        GridBagLayout controlLayout = new GridBagLayout();
        controlLayout.columnWidths = new int[]{0, 0, 0, 0, 0};
        controlLayout.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0};
        controlLayout.columnWeights = new double[]{0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
        controlLayout.rowWeights = new double[]{
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
        controls.setLayout(controlLayout);
        addHeading(controls, LabelFaction, 0);
        addHeading(controls, LabelRole, 1);
        addHeading(controls, LabelTier, 2);
        addHeading(controls, LabelRarity, 3);

        addControl(controls, factionControls, ShipFaction.Federation, ShipFaction.Federation.toString(), 0, 1, 7);
        addControl(controls, factionControls, ShipFaction.Klingon, ShipFaction.Klingon.toString(), 0, 2, 7);
        addControl(controls, factionControls, ShipFaction.Romulan, ShipFaction.Romulan.toString(), 0, 3, 7);
        addControl(controls, factionControls, ShipFaction.JemHadar, ShipFaction.JemHadar.toString(), 0, 4, 7);
        addControl(controls, factionControls, ShipFaction.Universal, ShipFaction.Universal.toString(), 0, 5, 7);

        addControl(controls, roleControls, Role.Eng, LabelEngineering, 1, 1, 7);
        addControl(controls, roleControls, Role.Tac, LabelTactical, 1, 2, 7);
        addControl(controls, roleControls, Role.Sci, LabelScience, 1, 3, 7);

        addControl(controls, tierControls, Tier.SmallCraft, Tier.SmallCraft.toString(), 2, 1, 7);
        addControl(controls, tierControls, Tier.Tier1, Tier.Tier1.toString(), 2, 2, 7);
        addControl(controls, tierControls, Tier.Tier2, Tier.Tier2.toString(), 2, 3, 7);
        addControl(controls, tierControls, Tier.Tier3, Tier.Tier3.toString(), 2, 4, 7);
        addControl(controls, tierControls, Tier.Tier4, Tier.Tier4.toString(), 2, 5, 7);
        addControl(controls, tierControls, Tier.Tier5, Tier.Tier5.toString(), 2, 6, 7);
        addControl(controls, tierControls, Tier.Tier6, Tier.Tier6.toString(), 2, 7, 7);

        addControl(controls, rarityControls, Rarity.Common, Rarity.Common.toString(), 3, 1, 6);
        addControl(controls, rarityControls, Rarity.Uncommon, Rarity.Uncommon.toString(), 3, 2, 6);
        addControl(controls, rarityControls, Rarity.Rare, Rarity.Rare.toString(), 3, 3, 6);
        addControl(controls, rarityControls, Rarity.VeryRare, Rarity.VeryRare.toString(), 3, 4, 6);
        addControl(controls, rarityControls, Rarity.UltraRare, Rarity.UltraRare.toString(), 3, 5, 6);
        addControl(controls, rarityControls, Rarity.Epic, Rarity.Epic.toString(), 3, 6, 6);

        taskPane.getContentPane().add(controls);
        return taskPane;
    }

    /**
     * Adds one enum-backed checkbox whose action derives and publishes one
     * complete immutable Ship Filter.
     *
     * @param controls  filter control grid
     * @param dimension controls belonging to one filter dimension
     * @param value     classified enum value governed by the checkbox
     * @param label     established visible label
     * @param column    zero-based dimension column
     * @param row       one-based row within the dimension
     * @param finalRow  last occupied row in the dimension
     * @param <T>       enum type for the filter dimension
     */
    private <T> void addControl(
            JPanel controls,
            Map<T, JCheckBox> dimension,
            T value,
            String label,
            int column,
            int row,
            int finalRow) {
        JCheckBox control = new JCheckBox(new FilterAction(label));
        control.setSelected(initiallyAllows(value));
        dimension.put(value, control);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = row == 1 ? 1.0 : 0.0;
        constraints.insets = new Insets(0, 0, row == finalRow ? 0 : 5, column == 3 ? 0 : 5);
        if (value == Rarity.Epic) {
            // Tier 5 makes this shared row taller; top alignment retains the legacy
            // Epic checkbox position instead of centering it two pixels lower.
            constraints.anchor = GridBagConstraints.NORTH;
        }
        constraints.gridx = column;
        constraints.gridy = row;
        controls.add(control, constraints);
    }

    /**
     * Resolves initial checkbox state from the same complete module-owned filter
     * that produces the first projection.
     *
     * @param value classified filter value
     * @return whether the initial Ship Filter allows the value
     */
    private boolean initiallyAllows(Object value) {
        if (value instanceof ShipFaction faction) {
            return filter.allowedFactions().contains(faction);
        }
        if (value instanceof Role role) {
            return filter.allowedRoles().contains(role);
        }
        if (value instanceof Tier tier) {
            return filter.allowedTiers().contains(tier);
        }
        if (value instanceof Rarity rarity) {
            return filter.allowedRarities().contains(rarity);
        }
        throw new IllegalArgumentException("Unsupported Ship Filter control value: " + value);
    }

    /**
     * Derives one complete immutable filter from all visible controls, computes
     * the final projection privately, and then publishes it once.
     */
    private void publishSelectedFilter() {
        Swing.requireEventDispatchThread("change a Ship Filter");
        ShipFilter<E, O> replacementFilter = filter
                .allowingFactions(selectedValues(factionControls))
                .allowingRoles(selectedValues(roleControls))
                .allowingTiers(selectedValues(tierControls))
                .allowingRarities(selectedValues(rarityControls));
        replaceState(replacementFilter, sourceEntries);
    }

    /**
     * Replaces the caller-owned entries atomically while retaining the current
     * Ship Filter and ordering.
     *
     * @param replacementEntries entries to project and display
     * @throws IllegalStateException if called outside the event-dispatch thread
     * @throws NullPointerException  if the collection, an entry, or required Ship
     *                               facts are null
     */
    public void present(Collection<? extends E> replacementEntries) {
        Swing.requireEventDispatchThread("present Ship Filter entries");
        List<E> replacement = List.copyOf(
                java.util.Objects.requireNonNull(replacementEntries, "entries"));
        replaceState(filter, replacement);
    }

    /**
     * Replaces the current typed ordering atomically while retaining source
     * entries, filter dimensions, and exact visible selection identities.
     *
     * @param order supported ordering paired with this entry type
     * @throws IllegalStateException if called outside the event-dispatch thread
     * @throws NullPointerException  if {@code order} is null
     */
    public void orderBy(O order) {
        Swing.requireEventDispatchThread("order Ship Filter entries");
        ShipFilter<E, O> replacementFilter = filter.withOrder(order);
        replaceState(replacementFilter, sourceEntries);
    }

    /**
     * Returns an immutable snapshot of selected entries in ascending visible
     * index order.
     *
     * @return immutable selected entries in visible order
     * @throws IllegalStateException if called outside the event-dispatch thread
     */
    List<E> selectedEntries() {
        Swing.requireEventDispatchThread("read Ship Filter selection");
        return List.copyOf(entries.getSelectedValuesList());
    }

    /**
     * Validates and projects a replacement filter/source pair before committing
     * it with the retained exact-identity selection as one final state.
     *
     * @param replacementFilter complete immutable filter
     * @param replacementSource immutable source-entry snapshot
     */
    private void replaceState(
            ShipFilter<E, O> replacementFilter,
            List<E> replacementSource) {
        List<E> selectedIdentities = selectedEntries();
        List<E> projection = replacementFilter.project(replacementSource);
        filter = replacementFilter;
        sourceEntries = replacementSource;
        publishProjection(projection, selectedIdentities);
    }

    /**
     * Publishes one completed projection and restores only exact selected entry
     * identities that remain visible. Clearing first prevents Swing from
     * transferring a retained index to a different entry.
     *
     * @param projection         complete validated visible entries
     * @param selectedIdentities entries selected before the projection changed
     */
    private void publishProjection(List<E> projection, List<E> selectedIdentities) {
        int changedSize = model.replace(projection);
        entries.clearSelection();
        for (int index = 0; index < projection.size(); index++) {
            if (containsIdentity(selectedIdentities, projection.get(index))) {
                entries.addSelectionInterval(index, index);
            }
        }
        // A contents-change can invalidate selected values without emitting a final
        // selection event, so synchronize details after identity restoration as well.
        updateDetails();
        model.publish(changedSize);
    }

    /**
     * Synchronizes optional Ship details with the current primary selection.
     */
    private void updateDetails() {
        if (details != null) {
            E selectedEntry = entries.getSelectedValue();
            details.setShip(selectedEntry == null ? null : filter.ship(selectedEntry));
        }
    }

    /**
     * List model that publishes each complete projection as one contents-change
     * event so observers never see intermediate filter state.
     *
     * @param <E> projected entry type
     */
    private static final class ProjectionListModel<E> extends AbstractListModel<E> {

        @Serial
        private static final long serialVersionUID = 1L;

        private List<E> projection = List.of();

        @Override
        public int getSize() {
            return projection.size();
        }

        @Override
        public E getElementAt(int index) {
            return projection.get(index);
        }

        /**
         * Replaces the visible immutable projection and reports exactly one
         * final-state event, including empty-to-empty updates.
         *
         * @param nextProjection complete validated projection
         * @return maximum old/new projection size used for the final event range
         */
        private int replace(List<E> nextProjection) {
            int changedSize = Math.max(projection.size(), nextProjection.size());
            projection = List.copyOf(nextProjection);
            return changedSize;
        }

        /**
         * Announces one previously installed final projection after selection and
         * details have been reconciled.
         *
         * @param changedSize maximum old/new projection size
         */
        private void publish(int changedSize) {
            fireContentsChanged(this, 0, Math.max(0, changedSize - 1));
        }
    }

    /**
     * Shared immutable-filter action behind every established checkbox. Using an
     * Action preserves the legacy checkbox sizing and tooltip presentation.
     */
    private final class FilterAction extends AbstractAction {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * Creates one labeled filter action.
         *
         * @param label established control label and tooltip
         */
        private FilterAction(String label) {
            super(label);
            putValue(SHORT_DESCRIPTION, label);
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            publishSelectedFilter();
        }
    }
}
