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
package com.kor.admiralty.ui;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.io.AdmiralsStore;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.panels.AdmiralPanel;
import com.kor.admiralty.ui.resources.ShipIconFactory;
import com.kor.admiralty.ui.resources.Swing;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import static com.kor.admiralty.ui.resources.Strings.AdmiraltyConsole.MsgConfirmDeleteQuestion;
import static com.kor.admiralty.ui.resources.Strings.AdmiraltyConsole.TitleConfirmDelete;

/**
 * Hosts one fixed workspace tab per application Admiral. All
 * construction and lifecycle operations are confined to the Swing event thread.
 */
public final class AdmiralWorkspaceHost implements PropertyChangeListener {

    private final JTabbedPane tabs;
    private final Admirals admirals;
    private final GameData gameData;
    private final AdmiralsStore admiralsStore;
    private final Path dataDirectory;
    private final ShipIconFactory iconRenderer;
    private final DeletionConfirmation deletionConfirmation;
    private final Map<Admiral, AdmiralPanel> workspacesByAdmiral;
    private final Map<AdmiralPanel, Admiral> admiralsByWorkspace;

    /**
     * Creates every startup workspace from the application-owned dependencies.
     *
     * @param tabs          outer Admiral tab container owned by AdmiraltyConsole
     * @param admirals      application-wide Admiral collection
     * @param gameData      read-only Ship, Assignment, and Event reference data
     * @param admiralsStore persistence used by workspace Roster transfer
     * @param dataDirectory resolved application data directory
     * @param iconRenderer  shared Ship presentation boundary
     * @throws NullPointerException  if any dependency is {@code null}
     * @throws IllegalStateException if construction occurs outside the Swing event thread
     */
    public AdmiralWorkspaceHost(
            JTabbedPane tabs,
            Admirals admirals,
            GameData gameData,
            AdmiralsStore admiralsStore,
            Path dataDirectory,
            ShipIconFactory iconRenderer) {
        this(
                tabs,
                admirals,
                gameData,
                admiralsStore,
                dataDirectory,
                iconRenderer,
                admiral -> confirmDeletion(tabs, admiral));
    }

    /**
     * Creates every startup workspace with a caller-supplied confirmation boundary
     * for headless host integration tests.
     *
     * @param tabs                 outer Admiral tab container owned by AdmiraltyConsole
     * @param admirals             application-wide Admiral collection
     * @param gameData             read-only Ship, Assignment, and Event reference data
     * @param admiralsStore        persistence used by workspace Roster transfer
     * @param dataDirectory        resolved application data directory
     * @param iconRenderer         shared Ship presentation boundary
     * @param deletionConfirmation selected-Admiral confirmation boundary
     * @throws NullPointerException  if any dependency is {@code null}
     * @throws IllegalStateException if construction occurs outside the Swing event thread
     */
    AdmiralWorkspaceHost(
            JTabbedPane tabs,
            Admirals admirals,
            GameData gameData,
            AdmiralsStore admiralsStore,
            Path dataDirectory,
            ShipIconFactory iconRenderer,
            DeletionConfirmation deletionConfirmation) {
        Swing.requireEventDispatchThread("construct Admiral workspace tabs");
        this.tabs = Objects.requireNonNull(tabs, "tabs");
        this.admirals = Objects.requireNonNull(admirals, "admirals");
        this.gameData = Objects.requireNonNull(gameData, "gameData");
        this.admiralsStore = Objects.requireNonNull(admiralsStore, "admiralsStore");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.iconRenderer = Objects.requireNonNull(iconRenderer, "iconRenderer");
        this.deletionConfirmation = Objects.requireNonNull(deletionConfirmation, "deletionConfirmation");
        // Runtime Admiral identity is the ownership key even if value equality is added
        // later; the inverse association resolves deletion without exposing an Admiral
        // getter from its fixed workspace.
        this.workspacesByAdmiral = new IdentityHashMap<Admiral, AdmiralPanel>();
        this.admiralsByWorkspace = new IdentityHashMap<AdmiralPanel, Admiral>();

        for (Admiral admiral : admirals.getAdmirals()) {
            addWorkspace(admiral);
        }
    }

    /**
     * Presents the production confirmation dialog using the host's Swing ancestry.
     */
    private static boolean confirmDeletion(JTabbedPane tabs, Admiral admiral) {
        String question = String.format(MsgConfirmDeleteQuestion, admiral.getName());
        int result = JOptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(tabs),
                question,
                TitleConfirmDelete,
                JOptionPane.YES_NO_OPTION);
        return result == JOptionPane.YES_OPTION;
    }

    /**
     * Adds one Admiral to the application collection and creates its single outer
     * workspace tab.
     *
     * @return the newly added Admiral permanently associated with the new workspace
     * @throws IllegalStateException if called outside the Swing event thread
     */
    public Admiral addAdmiral() {
        Swing.requireEventDispatchThread("add an Admiral workspace");
        Admiral admiral = admirals.addAdmiral();
        addWorkspace(admiral);
        return admiral;
    }

    /**
     * Confirms and removes exactly the selected Admiral workspace. Confirmation is
     * the mutation boundary: declining leaves every association and subscription
     * untouched; accepting releases listeners and associations before the Admiral.
     *
     * @return {@code true} when the selected Admiral was confirmed and removed
     * @throws IllegalStateException if called outside the Swing event thread
     */
    public boolean deleteSelectedAdmiral() {
        Swing.requireEventDispatchThread("delete an Admiral workspace");
        if (!(tabs.getSelectedComponent() instanceof AdmiralPanel workspace)) {
            return false;
        }
        Admiral admiral = admiralsByWorkspace.get(workspace);
        if (admiral == null || !deletionConfirmation.confirm(admiral)) {
            return false;
        }

        admiral.removePropertyChangeListener(this);
        workspace.dispose();
        workspacesByAdmiral.remove(admiral);
        admiralsByWorkspace.remove(workspace);
        tabs.remove(workspace);
        admirals.removeAdmiral(admiral);
        return true;
    }

    /**
     * Updates the outer title associated with one renamed Admiral.
     *
     * @param event committed Admiral property change
     * @throws IllegalStateException if invoked outside the Swing event thread
     */
    @Override
    public void propertyChange(PropertyChangeEvent event) {
        Swing.requireEventDispatchThread("update an Admiral tab title");
        if (!Admiral.PROP_NAME.equals(event.getPropertyName())) {
            return;
        }
        Admiral admiral = (Admiral) event.getSource();
        AdmiralPanel workspace = workspacesByAdmiral.get(admiral);
        int index = tabs.indexOfComponent(workspace);
        if (index >= 0) {
            tabs.setTitleAt(index, String.valueOf(event.getNewValue()));
        }
    }

    /**
     * Creates and registers one workspace root and its host title listener.
     */
    private void addWorkspace(Admiral admiral) {
        AdmiralPanel workspace = createWorkspace(admiral);
        tabs.addTab(admiral.getName(), workspace);
        workspacesByAdmiral.put(admiral, workspace);
        admiralsByWorkspace.put(workspace, admiral);
        admiral.addPropertyChangeListener(this);
    }

    /**
     * Creates one real workspace root from the host's fixed dependencies.
     */
    private AdmiralPanel createWorkspace(Admiral admiral) {
        return new AdmiralPanel(
                admiral,
                gameData,
                admiralsStore,
                dataDirectory,
                iconRenderer);
    }

    /**
     * Confirms whether one selected Admiral may cross the destructive boundary.
     */
    @FunctionalInterface
    interface DeletionConfirmation {

        /**
         * Returns {@code true} only when the selected Admiral should be removed.
         */
        boolean confirm(Admiral admiral);
    }
}
