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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.kor.admiralty.enums.PlayerFaction;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.io.GameData;

/**
 * Specifies identity-bearing Assignment solving through the public Admiral seam.
 */
class AdmiralSolverTest {

    /**
     * Solves the current Admiral state and returns the revision retained by its best Solution.
     *
     * @param admiral Admiral to solve
     * @return captured planning revision
     */
    private static long solvedRevision(Admiral admiral) {
        return admiral.solveAssignments().get(0).getPlanningRevision();
    }

    /**
     * Asserts a previously solved revision is stale and returns the newly captured revision.
     *
     * @param admiral          Admiral whose state has changed
     * @param previousRevision revision captured before the change
     * @return revision captured after the change
     */
    private static long assertSolutionInvalidated(Admiral admiral, long previousRevision) {
        long currentRevision = solvedRevision(admiral);
        assertNotEquals(previousRevision, currentRevision);
        return currentRevision;
    }

    /**
     * Sets the canonical Assignment requirements used by Solver behavior tests.
     *
     * @param assignment Assignment to configure
     * @param eng        required engineering
     * @param tac        required tactical
     * @param sci        required science
     */
    private static void configureAssignment(Assignment assignment, int eng, int tac, int sci) {
        assignment.setRequiredEng(eng);
        assignment.setRequiredTac(tac);
        assignment.setRequiredSci(sci);
    }

    /**
     * Creates representative canonical Ship facts for Solver tests.
     *
     * @param name canonical Ship name
     * @return a mutable Ship suitable for builder-created GameData
     */
    private static Ship ship(String name) {
        return ship(name, 10, 10, 10);
    }

    /**
     * Creates canonical Ship facts with explicit Admiralty statistics for scoring tests.
     *
     * @param name canonical Ship name
     * @param eng  engineering statistic
     * @param tac  tactical statistic
     * @param sci  science statistic
     * @return a mutable Ship suitable for builder-created GameData
     */
    private static Ship ship(String name, int eng, int tac, int sci) {
        return new ShipImpl(
                ShipFaction.Federation,
                Tier.Tier6,
                Rarity.Common,
                Role.Eng,
                name,
                eng,
                tac,
                sci,
                RuleType.All.rewardBonus(0),
                "");
    }

    /**
     * Verifies Admiral solves its own current state and retains the exact selected card and planning revision.
     */
    @Test
    void solvingCurrentAssignmentsRetainsRosterCardAndPlanningRevision() {
        Ship ship = ship("Planning Ship");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(ship)).build());
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        configureAssignment(admiral.getAssignment(0), 10, 10, 10);
        RosterCard rosterCard = admiral.getRoster().getActiveCards().get(0);
        long planningRevision = admiral.getPlanningRevision();

        CompositeSolution solution = admiral.solveAssignments().get(0);
        AssignmentSolution assignmentSolution = solution.getSolution(0);
        List<RosterCard> selectedCards = Arrays.stream(assignmentSolution.getRosterCards())
                .filter(card -> card != null)
                .collect(Collectors.toList());

        assertEquals(planningRevision, assignmentSolution.getPlanningRevision());
        assertEquals(planningRevision, solution.getPlanningRevision());
        assertEquals(List.of(rosterCard), solution.getRosterCards());
        assertEquals(1, selectedCards.size());
        assertSame(rosterCard, selectedCards.get(0));
    }

    /**
     * Verifies reusable overlap and duplicate One-Time copies remain independently selectable in a composite plan.
     */
    @Test
    void compositeSolutionDistinguishesReusableAndDuplicateOneTimeCards() {
        Ship sharedShip = ship("Shared Ship");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(sharedShip)).build());
        admiral.addReusableShips(List.of(sharedShip), RosterState.ACTIVE);
        admiral.adjustOneTimeShipQuantity(sharedShip, 2);
        admiral.setAssignmentCount(3);
        for (int index = 0; index < 3; index++) {
            configureAssignment(admiral.getAssignment(index), 10, 10, 10);
        }

        CompositeSolution solution = admiral.solveAssignments().stream().findFirst().orElseThrow();
        List<RosterCard> selectedCards = solution.getRosterCards();

        assertEquals(3, solution.size());
        assertEquals(3, selectedCards.size());
        assertEquals(3, new HashSet<RosterCardId>(selectedCards.stream()
                .map(RosterCard::getId)
                .collect(Collectors.toList())).size());
        assertEquals(1, selectedCards.stream()
                .filter(card -> card.getKind() == RosterCardKind.REUSABLE)
                .count());
        assertEquals(2, selectedCards.stream()
                .filter(card -> card.getKind() == RosterCardKind.ONE_TIME)
                .count());
        assertEquals(3, selectedCards.stream()
                .filter(card -> card.getShip() == sharedShip)
                .count());
    }

    /**
     * Verifies every Roster and Assignment fact used by planning invalidates Solutions calculated before it changed.
     */
    @Test
    void planningChangesInvalidatePriorSolutions() {
        Ship alpha = ship("Alpha");
        Ship beta = ship("Beta");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(alpha, beta)).build());
        admiral.addReusableShips(List.of(alpha), RosterState.ACTIVE);
        configureAssignment(admiral.getAssignment(0), 10, 10, 10);
        configureAssignment(admiral.getAssignment(1), 10, 10, 10);
        long previousRevision = solvedRevision(admiral);

        admiral.addReusableShips(List.of(beta), RosterState.ACTIVE);
        previousRevision = assertSolutionInvalidated(admiral, previousRevision);

        RosterCard betaCard = admiral.getRoster().getActiveCards().stream()
                .filter(card -> card.getShip() == beta)
                .findFirst()
                .orElseThrow();
        admiral.moveReusableCards(List.of(betaCard), RosterState.MAINTENANCE);
        previousRevision = assertSolutionInvalidated(admiral, previousRevision);

        admiral.adjustOneTimeShipQuantity(alpha, 1);
        previousRevision = assertSolutionInvalidated(admiral, previousRevision);

        admiral.getAssignment(0).setRequiredEng(9);
        previousRevision = assertSolutionInvalidated(admiral, previousRevision);

        admiral.setAssignmentCount(2);
        previousRevision = assertSolutionInvalidated(admiral, previousRevision);

        admiral.setPrioritizeActive(false);
        assertSolutionInvalidated(admiral, previousRevision);
    }

    /**
     * Verifies identity and usage facts outside planning leave a still-applicable Solution revision unchanged.
     */
    @Test
    void nonPlanningChangesPreserveSolutionRevision() {
        Ship ship = ship("Stable Planning Ship");
        GameData gameData = GameData.builder().ships(List.of(ship)).build();
        Admiral admiral = Admiral.restore(
                gameData,
                "Stable Admiral",
                PlayerFaction.Federation,
                List.of(),
                List.of(),
                List.of(),
                Map.of(ship, 4),
                true);
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        configureAssignment(admiral.getAssignment(0), 10, 10, 10);
        long planningRevision = solvedRevision(admiral);

        admiral.setName("Renamed Admiral");
        admiral.setFaction(PlayerFaction.Klingon);
        admiral.clearUsage();

        assertEquals(planningRevision, solvedRevision(admiral));
    }

    /**
     * Verifies Solver still ranks candidates from their canonical Ship statistics rather than card metadata.
     */
    @Test
    void canonicalShipFactsContinueToDetermineScores() {
        Ship weakAlpha = ship("Alpha", 1, 1, 1);
        Ship exactZulu = ship("Zulu", 10, 10, 10);
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(weakAlpha, exactZulu)).build());
        admiral.addReusableShips(List.of(weakAlpha, exactZulu), RosterState.ACTIVE);
        configureAssignment(admiral.getAssignment(0), 10, 10, 10);

        CompositeSolution solution = admiral.solveAssignments().get(0);

        assertEquals(0.0d, solution.getScore());
        assertSame(exactZulu, solution.getRosterCards().get(0).getShip());
    }

    /**
     * Verifies equal-scoring candidates retain the Roster's natural Ship ordering.
     */
    @Test
    void equalScoresRetainNaturalShipOrdering() {
        Ship alpha = ship("Alpha");
        Ship zulu = ship("Zulu");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(alpha, zulu)).build());
        admiral.addReusableShips(List.of(zulu, alpha), RosterState.ACTIVE);
        configureAssignment(admiral.getAssignment(0), 10, 10, 10);

        CompositeSolution solution = admiral.solveAssignments().get(0);

        assertSame(alpha, solution.getRosterCards().get(0).getShip());
    }

    /**
     * Verifies reusable-versus-One-Time priority remains the stable tie-break for overlapping canonical Ships.
     */
    @Test
    void reusableAndOneTimePriorityRemainsObservable() {
        Ship sharedShip = ship("Priority Ship");
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(sharedShip)).build());
        admiral.addReusableShips(List.of(sharedShip), RosterState.ACTIVE);
        admiral.adjustOneTimeShipQuantity(sharedShip, 1);
        configureAssignment(admiral.getAssignment(0), 10, 10, 10);

        CompositeSolution reusableFirst = admiral.solveAssignments().get(0);
        admiral.setPrioritizeActive(false);
        CompositeSolution oneTimeFirst = admiral.solveAssignments().get(0);

        assertEquals(reusableFirst.getScore(), oneTimeFirst.getScore());
        assertEquals(RosterCardKind.REUSABLE, reusableFirst.getRosterCards().get(0).getKind());
        assertEquals(RosterCardKind.ONE_TIME, oneTimeFirst.getRosterCards().get(0).getKind());
    }
}
