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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.kor.admiralty.beans;

import com.kor.admiralty.enums.*;
import com.kor.admiralty.io.GameData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Specifies atomic identity-bearing Solution deployment through the public
 * Admiral seam.
 */
class AdmiralDeploymentTest {

    /**
     * Sets representative requirements that one matching Ship satisfies exactly.
     *
     * @param assignment Assignment to configure
     */
    private static void configureAssignment(Assignment assignment) {
        assignment.setRequiredEng(10);
        assignment.setRequiredTac(10);
        assignment.setRequiredSci(10);
    }

    /**
     * Builds an identity-bearing composite Solution for defensive
     * deployment-validation scenarios.
     *
     * @param planningRevision current Admiral planning revision
     * @param cards            selected cards in slot order
     * @return a composite Solution carrying the supplied identities
     */
    private static CompositeSolution solution(long planningRevision, List<RosterCard> cards) {
        int[] indexes = new int[cards.size()];
        for (int index = 0; index < indexes.length; index++) {
            indexes[index] = index;
        }
        AssignmentSolution assignmentSolution = new AssignmentSolution(0, planningRevision, indexes);
        assignmentSolution.setRosterCards(cards);
        return new CompositeSolution(assignmentSolution);
    }

    /**
     * Locates one reusable card by canonical Ship in an immutable Roster view.
     *
     * @param roster Roster snapshot to inspect
     * @param ship   canonical Ship to locate
     * @return matching reusable card
     */
    private static RosterCard cardFor(RosterView roster, Ship ship) {
        return roster.getReusableCards().stream()
                .filter(card -> card.getShip() == ship)
                .findFirst()
                .orElseThrow();
    }

    /**
     * Creates canonical Ship facts for deployment tests.
     *
     * @param name canonical Ship name
     * @return a Ship suitable for builder-created GameData
     */
    private static Ship ship(String name) {
        return new ShipImpl(
                ShipFaction.Federation,
                Tier.Tier6,
                Rarity.Common,
                Role.Eng,
                name,
                10,
                10,
                10,
                RuleType.All.rewardBonus(0),
                "");
    }

    /**
     * Verifies reusable/One-Time overlap and multiple copies commit as one Roster
     * and usage transaction.
     */
    @Test
    void deploysReusableAndMultipleOneTimeCardsInOneCommittedChange() {
        Ship sharedShip = ship("Shared Deployment Ship");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(sharedShip)).build());
        admiral.addReusableShips(List.of(sharedShip), RosterState.ACTIVE);
        admiral.adjustOneTimeShipQuantity(sharedShip, 2);
        admiral.setAssignmentCount(3);
        for (int index = 0; index < 3; index++) {
            configureAssignment(admiral.getAssignment(index));
        }
        CompositeSolution solution = admiral.solveAssignments().getFirst();
        RosterView before = admiral.getRoster();
        long planningRevision = admiral.getPlanningRevision();
        List<RosterChange> committedChanges = new ArrayList<RosterChange>();
        List<Map<String, Integer>> listenerUsage = new ArrayList<Map<String, Integer>>();
        admiral.addRosterChangeListener(change -> {
            committedChanges.add(change);
            listenerUsage.add(Map.copyOf(admiral.getUsageCounts()));
        });

        Deployment deployment = assertInstanceOf(Deployment.class, admiral.deploySolution(solution));

        assertEquals(solution.getRosterCards(), deployment.getCards());
        assertEquals(1, deployment.getReusableCards().size());
        assertEquals(2, deployment.getOneTimeCards().size());
        assertSame(before, deployment.getRosterChange().getBefore());
        assertSame(admiral.getRoster(), deployment.getRosterChange().getAfter());
        assertEquals(RosterState.MAINTENANCE, admiral.getRoster().getReusableState(sharedShip));
        assertEquals(0, admiral.getRoster().getOneTimeQuantity(sharedShip));
        assertEquals(Map.of(sharedShip.getName(), 3), admiral.getUsageCounts());
        assertEquals(before.getRevision() + 1, admiral.getRoster().getRevision());
        assertEquals(planningRevision + 1, admiral.getPlanningRevision());
        assertEquals(List.of(deployment.getRosterChange()), committedChanges);
        assertEquals(List.of(Map.of(sharedShip.getName(), 3)), listenerUsage);
    }

    /**
     * Verifies a Solution calculated before a planning change is rejected without
     * touching Roster or usage.
     */
    @Test
    void rejectsStaleSolutionWithoutMutation() {
        Ship ship = ship("Stale Deployment Ship");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(ship)).build());
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        configureAssignment(admiral.getAssignment(0));
        CompositeSolution solution = admiral.solveAssignments().getFirst();
        admiral.getAssignment(0).setRequiredEng(11);
        RosterView before = admiral.getRoster();
        Map<String, Integer> usageBefore = Map.copyOf(admiral.getUsageCounts());
        List<RosterChange> rejectedChanges = new ArrayList<RosterChange>();
        admiral.addRosterChangeListener(rejectedChanges::add);

        DeploymentRejection rejection = assertInstanceOf(
                DeploymentRejection.class,
                admiral.deploySolution(solution));

        assertEquals(DeploymentRejectionReason.STALE_SOLUTION, rejection.getReason());
        assertEquals(solution.getPlanningRevision(), rejection.getSolutionPlanningRevision());
        assertEquals(admiral.getPlanningRevision(), rejection.getCurrentPlanningRevision());
        assertSame(before, admiral.getRoster());
        assertEquals(usageBefore, admiral.getUsageCounts());
        assertEquals(List.of(), rejectedChanges);
    }

    /**
     * Verifies one unavailable identity rejects a mixed batch before a valid Active
     * card is moved.
     */
    @Test
    void rejectsWholeBatchWhenOneSelectedCardIsUnavailable() {
        Ship alpha = ship("Available Alpha");
        Ship beta = ship("Unavailable Beta");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(alpha, beta)).build());
        admiral.addReusableShips(List.of(alpha, beta), RosterState.ACTIVE);
        RosterCard betaCard = cardFor(admiral.getRoster(), beta);
        admiral.moveReusableCards(List.of(betaCard), RosterState.MAINTENANCE);
        RosterCard alphaCard = cardFor(admiral.getRoster(), alpha);
        RosterCard unavailableBetaCard = cardFor(admiral.getRoster(), beta);
        CompositeSolution solution = solution(
                admiral.getPlanningRevision(),
                List.of(alphaCard, unavailableBetaCard));
        RosterView before = admiral.getRoster();
        List<RosterChange> rejectedChanges = new ArrayList<RosterChange>();
        admiral.addRosterChangeListener(rejectedChanges::add);

        DeploymentRejection rejection = assertInstanceOf(
                DeploymentRejection.class,
                admiral.deploySolution(solution));

        assertEquals(DeploymentRejectionReason.UNAVAILABLE_CARD, rejection.getReason());
        assertSame(before, admiral.getRoster());
        assertEquals(RosterState.ACTIVE, admiral.getRoster().getReusableState(alpha));
        assertEquals(RosterState.MAINTENANCE, admiral.getRoster().getReusableState(beta));
        assertEquals(Map.of(), admiral.getUsageCounts());
        assertEquals(List.of(), rejectedChanges);
    }

    /**
     * Verifies a repeated exact identity is a typed conflict rather than two
     * deployments of one card.
     */
    @Test
    void rejectsDuplicateCardIdentityWithoutMutation() {
        Ship ship = ship("Duplicate Deployment Ship");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(ship)).build());
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        RosterCard card = cardFor(admiral.getRoster(), ship);
        CompositeSolution solution = solution(admiral.getPlanningRevision(), List.of(card, card));
        RosterView before = admiral.getRoster();

        DeploymentRejection rejection = assertInstanceOf(
                DeploymentRejection.class,
                admiral.deploySolution(solution));

        assertEquals(DeploymentRejectionReason.DUPLICATE_CARD, rejection.getReason());
        assertSame(card, rejection.getCard());
        assertSame(before, admiral.getRoster());
        assertEquals(RosterState.ACTIVE, admiral.getRoster().getReusableState(ship));
        assertEquals(Map.of(), admiral.getUsageCounts());
    }

    /**
     * Verifies a batch retaining more local One-Time identities than remain
     * available reports the exact shortage.
     */
    @Test
    void rejectsInsufficientOneTimeQuantityWithoutMutation() {
        Ship ship = ship("Insufficient One-Time Ship");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(ship)).build());
        admiral.adjustOneTimeShipQuantity(ship, 2);
        List<RosterCard> originallyAvailable = admiral.getRoster().getOneTimeCards();
        admiral.adjustOneTimeShipQuantity(ship, -1);
        CompositeSolution solution = solution(admiral.getPlanningRevision(), originallyAvailable);
        RosterView before = admiral.getRoster();

        DeploymentRejection rejection = assertInstanceOf(
                DeploymentRejection.class,
                admiral.deploySolution(solution));

        assertEquals(DeploymentRejectionReason.INSUFFICIENT_ONE_TIME_QUANTITY, rejection.getReason());
        assertSame(ship, rejection.getShip());
        assertEquals(2, rejection.getRequestedQuantity());
        assertEquals(1, rejection.getAvailableQuantity());
        assertSame(before, admiral.getRoster());
        assertEquals(1, admiral.getRoster().getOneTimeQuantity(ship));
        assertEquals(Map.of(), admiral.getUsageCounts());
    }

    /**
     * Verifies null, empty, incomplete, and foreign-identity inputs fail loudly
     * before mutation.
     */
    @Test
    void callerMisuseFailsLoudlyBeforeMutation() {
        Ship ship = ship("Caller Misuse Ship");
        GameData gameData = GameData.builder().ships(List.of(ship)).build();
        Admiral local = new Admiral(gameData);
        Admiral foreign = new Admiral(gameData);
        local.addReusableShips(List.of(ship), RosterState.ACTIVE);
        foreign.addReusableShips(List.of(ship), RosterState.ACTIVE);
        RosterView before = local.getRoster();
        CompositeSolution foreignSolution = solution(
                local.getPlanningRevision(),
                foreign.getRoster().getActiveCards());
        CompositeSolution identityBearingSolution = solution(
                local.getPlanningRevision(),
                local.getRoster().getActiveCards());
        CompositeSolution partiallyIdentityBearingSolution = new CompositeSolution(
                identityBearingSolution.getSolution(0),
                new AssignmentSolution(0, local.getPlanningRevision(), 0));

        assertThrows(NullPointerException.class, () -> local.deploySolution(null));
        assertThrows(IllegalArgumentException.class, () -> local.deploySolution(new CompositeSolution()));
        assertThrows(IllegalArgumentException.class, () -> local.deploySolution(foreignSolution));
        assertThrows(
                IllegalArgumentException.class,
                () -> local.deploySolution(partiallyIdentityBearingSolution));

        assertSame(before, local.getRoster());
        assertEquals(RosterState.ACTIVE, local.getRoster().getReusableState(ship));
        assertEquals(Map.of(), local.getUsageCounts());
    }

    /**
     * Verifies usage overflow is detected before the Roster transaction commits.
     */
    @Test
    void usageOverflowFailsBeforeRosterMutation() {
        Ship ship = ship("Overflow Deployment Ship");
        GameData gameData = GameData.builder().ships(List.of(ship)).build();
        Admiral admiral = Admiral.restore(
                gameData,
                "Overflow Admiral",
                com.kor.admiralty.enums.PlayerFaction.Federation,
                List.of(),
                List.of(),
                List.of(),
                Map.of(ship, Integer.MAX_VALUE),
                true);
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        configureAssignment(admiral.getAssignment(0));
        CompositeSolution solution = admiral.solveAssignments().getFirst();
        RosterView before = admiral.getRoster();

        assertThrows(ArithmeticException.class, () -> admiral.deploySolution(solution));

        assertSame(before, admiral.getRoster());
        assertEquals(RosterState.ACTIVE, admiral.getRoster().getReusableState(ship));
        assertEquals(Map.of(ship.getName(), Integer.MAX_VALUE), admiral.getUsageCounts());
    }

    /**
     * Verifies a stale child injected beneath a current composite revision fails
     * loudly before deployment.
     */
    @Test
    void inconsistentChildPlanningRevisionFailsBeforeMutation() {
        Ship ship = ship("Inconsistent Revision Ship");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(ship)).build());
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        configureAssignment(admiral.getAssignment(0));
        CompositeSolution staleSolution = admiral.solveAssignments().getFirst();
        admiral.getAssignment(0).setRequiredEng(11);
        CompositeSolution currentSolution = admiral.solveAssignments().getFirst();
        currentSolution.solutions[0] = staleSolution.getSolution(0);
        RosterView before = admiral.getRoster();

        assertThrows(IllegalArgumentException.class, () -> admiral.deploySolution(currentSolution));

        assertSame(before, admiral.getRoster());
        assertEquals(RosterState.ACTIVE, admiral.getRoster().getReusableState(ship));
        assertEquals(Map.of(), admiral.getUsageCounts());
    }
}
