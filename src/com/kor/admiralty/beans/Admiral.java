/**
 * Copyright (C) 2015-2019 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.kor.admiralty.beans;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.kor.admiralty.Globals;
import com.kor.admiralty.enums.PlayerFaction;
import com.kor.admiralty.io.GameData;

/**
 * Authoritative runtime seam for one Admiral's Roster, Assignments, deployment, and usage history.
 * All mutation and synchronous listener delivery are caller-thread-confined, normally to the Swing event thread;
 * tests may call synchronously. Immutable snapshots provide stable observation without internal locking.
 */
public class Admiral {

    public static final String PROP_NAME = "name";
    public static final String PROP_FACTION = "faction";
    public static final String PROP_ASSIGNMENTCOUNT = "numAssignments";
    public static final String PROP_PRIORITIZEACTIVE = "prioritizeActive";
    public static final String PROP_ASSIGNMENTS = "assignments";
    private final Map<String, Integer> usage;
    private final PropertyChangeSupport change;
    private final Roster roster;
    private final List<RosterChangeListener> rosterChangeListeners;
    private final PropertyChangeListener planningAssignmentListener;
    private String name;
    private PlayerFaction faction;
    private int numAssignments;
    private boolean prioritizeActive;
    private List<Assignment> assignments;
    private long planningRevision;

    /**
     * Creates an Admiral that can resolve its empty Roster through the supplied reference data immediately.
     *
     * @param gameData read-only reference data used by lookup-dependent operations
     * @throws NullPointerException if {@code gameData} is null
     */
    public Admiral(GameData gameData) {
        this(
                gameData,
                "New Admiral",
                PlayerFaction.Federation,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap(),
                true);
    }

    /**
     * Initializes one construction-safe Admiral and its default empty current Assignments without publishing changes.
     * The supplied Roster and usage collections are validated and copied before the instance becomes observable.
     *
     * @param gameData         canonical reference data retained by the internal Roster
     * @param name             Admiral display name
     * @param faction          player faction
     * @param activeShips      canonical Active reusable Ships in stable Roster order
     * @param maintenanceShips canonical Maintenance reusable Ships in stable Roster order
     * @param oneTimeShips     repeated canonical One-Time Ships in stable Roster order
     * @param usageCounts      non-negative history keyed by canonical Ship
     * @param prioritizeActive whether reusable cards precede One-Time cards during solving
     * @throws IllegalArgumentException if a Ship is unknown or a usage count is negative
     * @throws ArithmeticException      if canonical usage aggregation overflows
     * @throws NullPointerException     if an argument, element, key, or value is null
     */
    private Admiral(
            GameData gameData,
            String name,
            PlayerFaction faction,
            Collection<Ship> activeShips,
            Collection<Ship> maintenanceShips,
            Collection<Ship> oneTimeShips,
            Map<Ship, Integer> usageCounts,
            boolean prioritizeActive) {
        GameData canonicalGameData = Objects.requireNonNull(gameData, "gameData");
        this.name = Objects.requireNonNull(name, "name");
        this.faction = Objects.requireNonNull(faction, "faction");
        this.roster = Roster.restore(canonicalGameData, activeShips, maintenanceShips, oneTimeShips);
        this.usage = canonicalUsage(canonicalGameData, usageCounts);
        this.numAssignments = 1;
        this.prioritizeActive = prioritizeActive;
        this.assignments = new ArrayList<Assignment>();
        this.change = new PropertyChangeSupport(this);
        this.rosterChangeListeners = new ArrayList<RosterChangeListener>();
        this.planningRevision = 0L;
        this.planningAssignmentListener = event -> advancePlanningRevision();
        for (int i = 0; i < Globals.MAX_ASSIGNMENTS; i++) {
            Assignment assignment = new Assignment();
            assignment.addPropertyChangeListener(planningAssignmentListener);
            this.assignments.add(assignment);
        }
    }

    /**
     * Restores a fully initialized Admiral from canonical runtime values without publishing startup changes.
     *
     * @param gameData         read-only reference data shared by the containing Admirals object
     * @param name             Admiral display name
     * @param faction          player faction
     * @param activeShips      canonical Active reusable Ships in stable Roster order
     * @param maintenanceShips canonical Maintenance reusable Ships in stable Roster order
     * @param oneTimeShips     repeated canonical One-Time Ships in stable Roster order
     * @param usageCounts      non-negative usage history keyed by canonical Ship
     * @param prioritizeActive whether reusable cards precede One-Time cards while solving
     * @return a lookup-ready Admiral at planning revision zero
     * @throws IllegalArgumentException if a Ship is unknown or a usage count is negative
     * @throws ArithmeticException      if canonical usage aggregation overflows
     * @throws NullPointerException     if an argument, element, key, or value is null
     */
    public static Admiral restore(
            GameData gameData,
            String name,
            PlayerFaction faction,
            Collection<Ship> activeShips,
            Collection<Ship> maintenanceShips,
            Collection<Ship> oneTimeShips,
            Map<Ship, Integer> usageCounts,
            boolean prioritizeActive) {
        return new Admiral(
                gameData,
                name,
                faction,
                activeShips,
                maintenanceShips,
                oneTimeShips,
                usageCounts,
                prioritizeActive);
    }

    /**
     * Validates and canonicalizes restored usage without retaining caller-owned collections.
     *
     * @param gameData    canonical reference data for this Admiral
     * @param usageCounts non-negative counts keyed by Ship-shaped canonical values
     * @return mutable canonical-name map owned exclusively by the new Admiral
     * @throws IllegalArgumentException if a Ship is unknown or a count is negative
     * @throws ArithmeticException      if two canonical entries overflow when combined
     * @throws NullPointerException     if the map, a key, or a value is null
     */
    private static Map<String, Integer> canonicalUsage(GameData gameData, Map<Ship, Integer> usageCounts) {
        Objects.requireNonNull(usageCounts, "usageCounts");
        Map<String, Integer> canonicalUsage = new HashMap<String, Integer>();
        for (Map.Entry<Ship, Integer> entry : usageCounts.entrySet()) {
            Ship suppliedShip = Objects.requireNonNull(entry.getKey(), "usageCounts contains null Ship");
            Integer count = Objects.requireNonNull(entry.getValue(), "usageCounts contains null count");
            if (count < 0) {
                throw new IllegalArgumentException("Usage count cannot be negative: " + suppliedShip.getName());
            }
            Ship canonicalShip = gameData.ship(suppliedShip.getName());
            if (canonicalShip == null) {
                throw new IllegalArgumentException("Usage Ship is not present in this Admiral's GameData: "
                        + suppliedShip.getName());
            }
            String canonicalName = canonicalShip.getName();
            canonicalUsage.put(
                    canonicalName,
                    Math.addExact(canonicalUsage.getOrDefault(canonicalName, 0), count));
        }
        return canonicalUsage;
    }

    /**
     * Returns one immutable snapshot containing reusable and One-Time Roster state at a single planning revision.
     * Mutation and listener delivery are caller-thread-confined, normally to the Swing event thread.
     *
     * @return the current complete Roster view
     */
    public RosterView getRoster() {
        return roster.view();
    }

    /**
     * Returns the revision of the Roster, Assignment, count, and priority facts used for planning.
     * Name, faction, and usage-only changes deliberately do not advance this revision.
     *
     * @return current non-negative planning revision
     */
    public long getPlanningRevision() {
        return planningRevision;
    }

    /**
     * Adds reusable Ships to a destination, atomically moving any card already in the other reusable state.
     * A same-destination add is a no-op and does not advance the Roster revision.
     *
     * @param ships       Ships to resolve canonically through this Admiral's GameData
     * @param destination Active or Maintenance
     * @throws IllegalArgumentException if a Ship is unknown or the destination is Absent
     * @throws NullPointerException     if an argument or collection element is null
     */
    public void addReusableShips(Collection<Ship> ships, RosterState destination) {
        mutateRoster(() -> roster.addReusableShips(ships, destination));
    }

    /**
     * Moves reusable cards identified by a prior immutable Roster view in one atomic operation.
     *
     * @param cards       cards belonging to this Admiral, from any still-applicable view
     * @param destination Active or Maintenance
     * @throws IllegalArgumentException if a card is foreign or removed, or the destination is Absent
     * @throws NullPointerException     if an argument or collection element is null
     */
    public void moveReusableCards(Collection<RosterCard> cards, RosterState destination) {
        mutateRoster(() -> roster.moveReusableCards(cards, destination));
    }

    /**
     * Removes reusable cards identified by a prior immutable Roster view in one atomic operation.
     *
     * @param cards cards belonging to this Admiral, from any still-applicable view
     * @throws IllegalArgumentException if a card is foreign or was already removed
     * @throws NullPointerException     if {@code cards} or one of its elements is null
     */
    public void removeReusableCards(Collection<RosterCard> cards) {
        mutateRoster(() -> roster.removeReusableCards(cards));
    }

    /**
     * Adjusts one One-Time Ship quantity while preserving the identities of copies that remain available.
     * Quantity zero removes that One-Time Ship type from the Roster.
     *
     * @param ship       Ship to resolve canonically through this Admiral's GameData
     * @param adjustment signed quantity change
     * @throws IllegalArgumentException if the Ship is unknown or the resulting quantity would be negative
     * @throws ArithmeticException      if the resulting quantity exceeds the integer range
     * @throws NullPointerException     if {@code ship} is null
     */
    public void adjustOneTimeShipQuantity(Ship ship, int adjustment) {
        adjustOneTimeShipQuantities(List.of(ship), adjustment);
    }

    /**
     * Applies one signed quantity adjustment per supplied One-Time Ship occurrence in a single transaction.
     * The whole batch is validated before any quantity or card identity changes.
     *
     * @param ships                   Ships to resolve canonically through this Admiral's GameData
     * @param adjustmentPerOccurrence signed quantity change applied to each occurrence
     * @throws IllegalArgumentException if a Ship is unknown or any resulting quantity would be negative
     * @throws ArithmeticException      if an adjustment or resulting quantity exceeds the integer range
     * @throws NullPointerException     if {@code ships} or one of its elements is null
     */
    public void adjustOneTimeShipQuantities(Collection<Ship> ships, int adjustmentPerOccurrence) {
        mutateRoster(() -> roster.adjustOneTimeShipQuantities(ships, adjustmentPerOccurrence));
    }

    /**
     * Registers a caller-thread listener for committed reusable and One-Time Roster changes.
     *
     * @param listener listener to notify after each successful operation
     * @throws NullPointerException if {@code listener} is null
     */
    public void addRosterChangeListener(RosterChangeListener listener) {
        rosterChangeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Stops a previously registered Roster listener from receiving future changes.
     *
     * @param listener listener to remove
     */
    public void removeRosterChangeListener(RosterChangeListener listener) {
        rosterChangeListeners.remove(listener);
    }

    /**
     * Executes one Roster transaction and publishes only a committed change.
     *
     * @param mutation deferred Roster operation, returning null for a no-op
     * @throws RuntimeException if validation or commit fails; no listeners are notified in that case
     */
    private void mutateRoster(Supplier<RosterChange> mutation) {
        RosterChange rosterChange = mutation.get();
        publishRosterChange(rosterChange);
    }

    /**
     * Advances planning state and notifies every listener after the Roster commit.
     *
     * @param rosterChange committed change, or null when the mutation was a no-op
     */
    private void publishRosterChange(RosterChange rosterChange) {
        if (rosterChange == null) {
            return;
        }

        // Listeners that solve in response to a committed Roster change must capture the new planning revision.
        advancePlanningRevision();
        // A snapshot permits listeners to add or remove subscriptions safely during synchronous notification.
        for (RosterChangeListener listener : new ArrayList<RosterChangeListener>(rosterChangeListeners)) {
            listener.rosterChanged(rosterChange);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        String oldName = this.name;
        this.name = name;
        change.firePropertyChange(PROP_NAME, oldName, this.name);
    }

    public PlayerFaction getFaction() {
        return faction;
    }

    public void setFaction(PlayerFaction faction) {
        PlayerFaction oldFaction = this.faction;
        this.faction = faction;
        change.firePropertyChange(PROP_FACTION, oldFaction, this.faction);
    }

    /**
     * Returns canonical usage history without exposing mutable Admiral state.
     *
     * @return unmodifiable usage counts keyed by canonical Ship name
     */
    public Map<String, Integer> getUsageCounts() {
        return Collections.unmodifiableMap(new HashMap<String, Integer>(usage));
    }

    /**
     * Clears deployment history without changing the Roster or planning revision.
     */
    public void clearUsage() {
        this.usage.clear();
    }

    public int getAssignmentCount() {
        return numAssignments;
    }

    /**
     * Selects how many current Assignments participate in planning.
     *
     * @param numAssignments number of Assignments Solver should cover
     */
    public void setAssignmentCount(int numAssignments) {
        int oldNum = this.numAssignments;
        if (oldNum != numAssignments) {
            advancePlanningRevision();
        }
        this.numAssignments = numAssignments;
        change.firePropertyChange(PROP_ASSIGNMENTCOUNT, oldNum, this.numAssignments);
    }

    public boolean getPrioritizeActive() {
        return prioritizeActive;
    }

    /**
     * Selects whether equal-scoring reusable cards precede One-Time cards during planning.
     *
     * @param prioritizeActive {@code true} for reusable cards first; {@code false} for One-Time cards first
     */
    public void setPrioritizeActive(boolean prioritizeActive) {
        boolean oldVal = this.prioritizeActive;
        if (oldVal != prioritizeActive) {
            advancePlanningRevision();
        }
        this.prioritizeActive = prioritizeActive;
        change.firePropertyChange(PROP_PRIORITIZEACTIVE, oldVal, this.prioritizeActive);
    }

    /**
     * Returns the current Assignment objects in slot order without exposing structural list mutation.
     * Assignment field changes remain observable and advance the planning revision.
     *
     * @return unmodifiable current Assignment list
     */
    public List<Assignment> getAssignments() {
        return Collections.unmodifiableList(new ArrayList<Assignment>(assignments));
    }

    /**
     * Replaces the current Assignment slots and transfers planning-change observation to the replacements.
     *
     * @param assignments non-null Assignment objects in slot order
     * @throws NullPointerException if the list or an Assignment is null
     */
    public void setAssignments(List<Assignment> assignments) {
        Objects.requireNonNull(assignments, "assignments");
        ArrayList<Assignment> replacement = new ArrayList<Assignment>(assignments.size());
        for (Assignment assignment : assignments) {
            replacement.add(Objects.requireNonNull(assignment, "assignments contains null"));
        }
        ArrayList<Assignment> oldList = new ArrayList<Assignment>(this.assignments);
        if (!oldList.equals(replacement)) {
            advancePlanningRevision();
        }
        removeAssignmentPlanningListeners(oldList);
        this.assignments = replacement;
        addAssignmentPlanningListeners(replacement);
        change.firePropertyChange(PROP_ASSIGNMENTS, oldList, this.assignments);
    }

    public Assignment getAssignment(int index) {
        if (index < 0) return null;
        if (index >= assignments.size()) return null;
        return assignments.get(index);
    }

    /**
     * Deploys one identity-bearing Solution as a single Roster and usage transaction.
     * Expected conflicts are returned as structured outcomes; invalid caller input fails before mutation.
     *
     * @param solution Solution calculated through this Admiral
     * @return immutable deployment information or a typed rejection
     * @throws IllegalArgumentException if the Solution is incomplete, empty, or contains a foreign identity
     * @throws NullPointerException     if {@code solution} or a caller-mutated child Solution is null
     * @throws ArithmeticException      if a revision or usage counter would overflow
     */
    public DeploymentOutcome deploySolution(CompositeSolution solution) {
        Objects.requireNonNull(solution, "solution");
        if (!solution.hasCompleteRosterCardSelection()) {
            throw new IllegalArgumentException("Solution does not contain a complete Roster-card selection");
        }
        List<RosterCard> selectedCards = solution.getRosterCards();
        if (selectedCards.isEmpty()) {
            throw new IllegalArgumentException("Solution must contain at least one selected Roster card");
        }
        roster.requireOwnedCardIdentities(selectedCards);
        if (solution.getPlanningRevision() != planningRevision) {
            return DeploymentRejection.stale(solution.getPlanningRevision(), planningRevision);
        }
        Roster.DeploymentPlan deploymentPlan = roster.prepareDeployment(selectedCards);
        DeploymentRejection rejection = deploymentPlan.getRejection();
        if (rejection != null) {
            return rejection;
        }

        Map<String, Integer> updatedUsage = new HashMap<String, Integer>(usage);
        for (RosterCard card : deploymentPlan.getCards()) {
            String shipName = card.getShip().getName();
            updatedUsage.put(shipName, Math.addExact(updatedUsage.getOrDefault(shipName, 0), 1));
        }
        // Prevalidate the Admiral revision before the Roster commits so overflow cannot split the transaction.
        Math.incrementExact(planningRevision);

        RosterChange rosterChange = roster.commitDeployment(deploymentPlan);
        usage.clear();
        usage.putAll(updatedUsage);
        publishRosterChange(rosterChange);
        return new Deployment(deploymentPlan.getCards(), rosterChange);
    }

    /**
     * Solves the current Assignments from the current deployable Roster cards.
     * Canonical Ship facts drive scoring while each Solution retains card identity and this planning revision.
     *
     * @return the best composite Solutions in score order
     */
    public List<CompositeSolution> solveAssignments() {
        RosterView currentRoster = getRoster();
        Assignment assignment1 = numAssignments >= 1 ? assignments.get(0) : null;
        Assignment assignment2 = numAssignments >= 2 ? assignments.get(1) : null;
        Assignment assignment3 = numAssignments >= 3 ? assignments.get(2) : null;
        return Solver.solve(
                assignment1,
                assignment2,
                assignment3,
                currentRoster.getDeployableCards(prioritizeActive),
                Globals.SOLVER_DEPTH,
                planningRevision);
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        change.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        change.removePropertyChangeListener(l);
    }

    /**
     * Attaches planning listeners to distinct current Assignment objects.
     *
     * @param currentAssignments Assignments whose content affects solving
     */
    private void addAssignmentPlanningListeners(Collection<Assignment> currentAssignments) {
        for (Assignment assignment : new LinkedHashSet<Assignment>(currentAssignments)) {
            assignment.addPropertyChangeListener(planningAssignmentListener);
        }
    }

    /**
     * Detaches planning listeners from Assignment objects that no longer belong to this Admiral.
     *
     * @param previousAssignments Assignments being replaced
     */
    private void removeAssignmentPlanningListeners(Collection<Assignment> previousAssignments) {
        for (Assignment assignment : new LinkedHashSet<Assignment>(previousAssignments)) {
            assignment.removePropertyChangeListener(planningAssignmentListener);
        }
    }

    /**
     * Advances the revision captured by newly calculated Solutions.
     *
     * @throws ArithmeticException if the revision counter overflows
     */
    private void advancePlanningRevision() {
        planningRevision = Math.incrementExact(planningRevision);
    }

    @Override
    public String toString() {
        RosterView currentRoster = getRoster();
        return name + " {\n\tFaction: " + faction + "\n\tActive Ships: " +
                currentRoster.getActiveCards().size() + "\n\tMaintenance Ships: "
                + currentRoster.getMaintenanceCards().size() +
                "\n\tOne Time Ships: " + currentRoster.getOneTimeCards().size() +
                "\n\tShip Usage: " + usage.size() + "\n}";
    }

}
