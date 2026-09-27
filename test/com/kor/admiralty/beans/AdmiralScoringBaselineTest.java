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
package com.kor.admiralty.beans;

import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.rewards.RewardMaintenanceReduction;
import com.kor.admiralty.rewards.RewardNothing;
import com.kor.admiralty.rewards.RewardStat;
import com.kor.admiralty.rules.AlwaysApply;
import com.kor.admiralty.rules.And;
import com.kor.admiralty.rules.PerShipCategory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Captures the scores and exact Roster-card choices returned by Admiral before
 * Solution scoring is reorganized.
 */
class AdmiralScoringBaselineTest {

    /** Gives one Eng point for each empty Ship-pair target slot. */
    private static final class EmptyTargetAbility extends SpecialAbility {
        private EmptyTargetAbility() {
            super(new RewardNothing());
        }

        /** Makes visits to empty target slots observable in Admiral's scored total. */
        @Override
        public void procShip(ScoringEffects effects, Ship source, Ship target) {
            if (target == null) {
                effects.addStats(1, 0, 0);
            }
        }

        @Override
        public void procAssignment(ScoringEffects effects, Assignment assignment) {
        }

        @Override
        public void procCriticals(ScoringEffects effects, Assignment assignment) {
        }

        @Override
        public String toParamString() {
            return "EmptyTargetAbility";
        }
    }

    /** Creates a canonical Ship with explicit scoring facts and Special Ability. */
    private static ShipImpl ship(String name, Role role, int eng, int tac, int sci, SpecialAbility ability) {
        return ship(name, Tier.Tier6, role, eng, tac, sci, ability);
    }

    /** Creates a canonical Ship with a selectable tier for natural-order cases. */
    private static ShipImpl ship(String name, Tier tier, Role role, int eng, int tac, int sci, SpecialAbility ability) {
        return new ShipImpl(ShipFaction.Federation, tier, Rarity.Common, role,
                name, eng, tac, sci, ability, "");
    }

    /** Creates an Admiral whose Roster can hold the supplied canonical Ships. */
    private static Admiral admiral(Ship... ships) {
        return new Admiral(GameData.builder().ships(Arrays.asList(ships)).build());
    }

    /** Sets the requirements for one of the Admiral's current Assignments. */
    private static void require(Assignment assignment, int eng, int tac, int sci) {
        assignment.setRequiredEng(eng);
        assignment.setRequiredTac(tac);
        assignment.setRequiredSci(sci);
    }

    /** Returns the exact Active Roster card for a canonical Ship. */
    private static RosterCard activeCard(Admiral admiral, Ship ship) {
        return admiral.getRoster().getActiveCards().stream()
                .filter(card -> card.getShip() == ship)
                .findFirst().orElseThrow();
    }

    /**
     * Finds a scored choice by selected card identity through Admiral, independent
     * of where that choice ranks among the returned top ten.
     */
    private static AssignmentSolution solutionFor(Admiral admiral, RosterCard... expectedCards) {
        for (CompositeSolution composite : admiral.solveAssignments()) {
            List<RosterCard> cards = composite.getRosterCards();
            if (cards.size() != expectedCards.length) {
                continue;
            }
            boolean matches = true;
            for (int index = 0; index < expectedCards.length; index++) {
                if (cards.get(index) != expectedCards[index]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return composite.getSolution(0);
            }
        }
        return fail("Admiral did not retain the expected Roster-card choice");
    }

    /** Verifies the exact cards in one Assignment's occupied slots and their order. */
    private static void assertCards(AssignmentSolution solution, RosterCard... expectedCards) {
        RosterCard[] selected = solution.getRosterCards();
        assertEquals(3, selected.length);
        for (int index = 0; index < expectedCards.length; index++) {
            assertSame(expectedCards[index], selected[index]);
        }
        for (int index = expectedCards.length; index < selected.length; index++) {
            assertNull(selected[index]);
        }
    }

    /** Verifies one selected card per Assignment and their composite order. */
    private static void assertSingleCardPerAssignment(CompositeSolution solution, RosterCard... expectedCards) {
        assertEquals(expectedCards.length, solution.size());
        assertEquals(expectedCards.length, solution.getRosterCards().size());
        for (int index = 0; index < expectedCards.length; index++) {
            assertCards(solution.getSolution(index), expectedCards[index]);
            assertSame(expectedCards[index], solution.getRosterCards().get(index));
        }
    }

    /**
     * A pair bonus fires once for each eligible companion and does not fire for
     * an empty slot.
     */
    @Test
    void shipPairBonusAppliesOncePerEligibleCompanion() {
        Ship source = ship("Alpha", Role.Eng, 10, 0, 0, RuleType.PerAnyShip.rewardEng(5));
        Ship second = ship("Beta", Role.Eng, 0, 0, 0, RuleType.All.rewardNothing());
        Ship third = ship("Gamma", Role.Eng, 0, 0, 0, RuleType.All.rewardNothing());
        Admiral admiral = admiral(source, second, third);
        admiral.addReusableShips(List.of(source, second, third), RosterState.ACTIVE);
        require(admiral.getAssignment(0), 20, 0, 0);
        RosterCard alpha = activeCard(admiral, source);
        RosterCard beta = activeCard(admiral, second);
        RosterCard gamma = activeCard(admiral, third);

        AssignmentSolution alone = solutionFor(admiral, alpha);
        AssignmentSolution pair = solutionFor(admiral, beta, alpha);
        AssignmentSolution triple = solutionFor(admiral, gamma, beta, alpha);

        assertEquals(10, alone.getEng());
        assertEquals(5.0d, alone.getScore());
        assertEquals(15, pair.getEng());
        assertEquals(2.5d, pair.getScore());
        assertEquals(20, triple.getEng());
        assertEquals(0.0d, triple.getScore());
        assertCards(triple, gamma, beta, alpha);
    }

    /** A selected Ship visits both empty target slots during its pair phase. */
    @Test
    void shipPairEffectsReceiveEmptyTargetSlots() {
        Ship ship = ship("Empty Targets", Role.Eng, 8, 0, 0, new EmptyTargetAbility());
        Admiral admiral = admiral(ship);
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        require(admiral.getAssignment(0), 10, 0, 0);

        AssignmentSolution solution = solutionFor(admiral, activeCard(admiral, ship));

        assertEquals(10, solution.getEng());
        assertEquals(0.0d, solution.getScore());
    }

    /** A role repeated in one ability grants its reward once for each entry. */
    @Test
    void repeatedShipCategoriesApplyRepeatedly() {
        Ship source = ship("Category Source", Role.Eng, 10, 0, 0,
                new PerShipCategory(new RewardStat(5, 0, 0), Role.Tac, Role.Tac));
        Ship target = ship("Category Target", Role.Tac, 0, 0, 0,
                RuleType.All.rewardNothing());
        Admiral admiral = admiral(source, target);
        admiral.addReusableShips(List.of(source, target), RosterState.ACTIVE);
        require(admiral.getAssignment(0), 20, 0, 0);

        AssignmentSolution solution = solutionFor(admiral,
                activeCard(admiral, target), activeCard(admiral, source));

        assertEquals(20, solution.getEng());
        assertEquals(0.0d, solution.getScore());
    }

    /** A WhenAlone reward depends on occupied slots without reading candidate indexes. */
    @Test
    void whenAloneRewardAppliesOnlyToSingleShipChoices() {
        Ship source = ship("Alone Source", Role.Eng, 10, 0, 0,
                RuleType.WhenAlone.rewardEng(5));
        Ship companion = ship("Alone Companion", Role.Eng, 0, 0, 0,
                RuleType.All.rewardNothing());
        Admiral admiral = admiral(source, companion);
        admiral.addReusableShips(List.of(source, companion), RosterState.ACTIVE);
        require(admiral.getAssignment(0), 15, 0, 0);
        RosterCard sourceCard = activeCard(admiral, source);
        RosterCard companionCard = activeCard(admiral, companion);

        AssignmentSolution alone = solutionFor(admiral, sourceCard);
        AssignmentSolution paired = solutionFor(admiral, sourceCard, companionCard);

        assertEquals(15, alone.getEng());
        assertEquals(0.0d, alone.getScore());
        assertEquals(10, paired.getEng());
        assertEquals(50.0d / 15.0d, paired.getScore());
    }

    /** Maintenance rewards remain representable but do not change a scored choice. */
    @Test
    void maintenanceRewardHasNoScoringEffect() {
        Ship ship = ship("Maintenance", Role.Eng, 10, 0, 0,
                new AlwaysApply(new RewardMaintenanceReduction(0.5d)));
        Admiral admiral = admiral(ship);
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        require(admiral.getAssignment(0), 10, 0, 0);

        AssignmentSolution solution = solutionFor(admiral, activeCard(admiral, ship));

        assertEquals(10, solution.getEng());
        assertEquals(0.0d, solution.getScore());
    }

    /** Assignment-wide stats apply once after the base Ship stats are collected. */
    @Test
    void assignmentWideBonusChangesDisplayedTotalsAndScore() {
        Ship ship = ship("Assignment Bonus", Role.Eng, 5, 5, 5, RuleType.All.rewardAll(5));
        Admiral admiral = admiral(ship);
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        require(admiral.getAssignment(0), 10, 10, 10);
        RosterCard card = activeCard(admiral, ship);

        AssignmentSolution solution = solutionFor(admiral, card);

        assertEquals(10, solution.getEng());
        assertEquals(10, solution.getTac());
        assertEquals(10, solution.getSci());
        assertEquals(0, solution.getCritRate());
        assertEquals(0.0d, solution.getScore());
        assertCards(solution, card);
    }

    /** Event requirements and the Event critical rate both affect the final score. */
    @Test
    void eventStatsAndCriticalRateChangeTheDisplayedScore() {
        Ship ship = ship("Event Ship", Role.Eng, 10, 5, 0, RuleType.All.rewardNothing());
        Admiral admiral = admiral(ship);
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        Assignment assignment = admiral.getAssignment(0);
        require(assignment, 10, 5, 0);
        RosterCard card = activeCard(admiral, ship);
        assertEquals(0.0d, solutionFor(admiral, card).getScore());

        assignment.setEventEng(2);
        assignment.setEventTac(1);
        assignment.setEventCritRate(4);
        AssignmentSolution solution = solutionFor(admiral, card);

        assertEquals(10, solution.getEng());
        assertEquals(5, solution.getTac());
        assertEquals(4, solution.getCritRate());
        assertEquals(34.0d / 18.0d, solution.getScore());
    }

    /**
     * A pair's stat bonus feeds the later critical multiplier and the Assignment's
     * target critical rate contributes only to the score.
     */
    @Test
    void pairStatsFeedTheLaterCriticalPhase() {
        Ship source = ship("Engineering", Role.Eng, 10, 0, 0, RuleType.PerTac.rewardEng(5));
        Ship target = ship("Tactical", Role.Tac, 0, 0, 0,
                RuleType.MultiplyCriticalRate.rewardMultiplyCritRate(2, 1, 1));
        Admiral admiral = admiral(source, target);
        admiral.addReusableShips(List.of(source, target), RosterState.ACTIVE);
        Assignment assignment = admiral.getAssignment(0);
        require(assignment, 10, 0, 0);
        assignment.setTargetCritChance(33);
        RosterCard engineering = activeCard(admiral, source);
        RosterCard tactical = activeCard(admiral, target);

        AssignmentSolution solution = solutionFor(admiral, tactical, engineering);

        assertEquals(15, solution.getEng());
        assertEquals(10, solution.getCritRate());
        assertEquals(0.1d, solution.getScore());
        assertCards(solution, tactical, engineering);
    }

    /**
     * An Assignment-phase ignore reward overwrites all three flags left by a
     * Ship-pair reward, including the flags it sets to false.
     */
    @Test
    void assignmentPhaseOverwritesPairPhaseIgnoreEventFlags() {
        Ship engineering = ship("Engineering", Role.Eng, 10, 0, 0,
                RuleType.PerTac.rewardIgnoreEng());
        Ship tactical = ship("Tactical", Role.Tac, 0, 10, 0,
                RuleType.All.rewardIgnoreTac());
        Admiral admiral = admiral(engineering, tactical);
        admiral.addReusableShips(List.of(engineering, tactical), RosterState.ACTIVE);
        Assignment assignment = admiral.getAssignment(0);
        require(assignment, 10, 10, 0);
        assignment.setEventEng(5);
        assignment.setEventTac(7);
        RosterCard engineeringCard = activeCard(admiral, engineering);
        RosterCard tacticalCard = activeCard(admiral, tactical);

        AssignmentSolution solution = solutionFor(admiral, tacticalCard, engineeringCard);

        assertEquals(10, solution.getEng());
        assertEquals(10, solution.getTac());
        assertEquals(0, solution.getCritRate());
        assertEquals(2.0d, solution.getScore());
        assertCards(solution, tacticalCard, engineeringCard);
    }

    /**
     * Assignment abilities run in selected slot order, so the later Ship's
     * ignore-Event flags replace the earlier Ship's flags.
     */
    @Test
    void assignmentAbilitiesPreserveSelectedShipOrder() {
        Ship alpha = ship("Alpha", Role.Eng, 5, 5, 0,
                RuleType.Ignore.rewardIgnoreEng());
        Ship beta = ship("Beta", Role.Eng, 5, 5, 0,
                RuleType.Ignore.rewardIgnoreTac());
        Admiral admiral = admiral(alpha, beta);
        admiral.addReusableShips(List.of(alpha, beta), RosterState.ACTIVE);
        Assignment assignment = admiral.getAssignment(0);
        require(assignment, 10, 10, 0);
        assignment.setEventEng(5);
        assignment.setEventTac(7);
        RosterCard alphaCard = activeCard(admiral, alpha);
        RosterCard betaCard = activeCard(admiral, beta);

        AssignmentSolution solution = solutionFor(admiral, betaCard, alphaCard);

        assertEquals(10, solution.getEng());
        assertEquals(10, solution.getTac());
        assertEquals(70.0d / 27.0d, solution.getScore());
        assertCards(solution, betaCard, alphaCard);
    }

    /** Later compound children overwrite the earlier child's ignore-Event flags. */
    @Test
    void compoundAbilityPreservesChildOrder() {
        Ship engThenTac = ship("Eng Then Tac", Role.Eng, 10, 10, 0,
                new And(RuleType.All.rewardIgnoreEng(), RuleType.All.rewardIgnoreTac()));
        Admiral firstAdmiral = admiral(engThenTac);
        firstAdmiral.addReusableShips(List.of(engThenTac), RosterState.ACTIVE);
        Assignment firstAssignment = firstAdmiral.getAssignment(0);
        require(firstAssignment, 10, 10, 0);
        firstAssignment.setEventEng(5);
        firstAssignment.setEventTac(7);

        Ship tacThenEng = ship("Tac Then Eng", Role.Eng, 10, 10, 0,
                new And(RuleType.All.rewardIgnoreTac(), RuleType.All.rewardIgnoreEng()));
        Admiral secondAdmiral = admiral(tacThenEng);
        secondAdmiral.addReusableShips(List.of(tacThenEng), RosterState.ACTIVE);
        Assignment secondAssignment = secondAdmiral.getAssignment(0);
        require(secondAssignment, 10, 10, 0);
        secondAssignment.setEventEng(5);
        secondAssignment.setEventTac(7);

        AssignmentSolution first = solutionFor(firstAdmiral, activeCard(firstAdmiral, engThenTac));
        AssignmentSolution second = solutionFor(secondAdmiral, activeCard(secondAdmiral, tacThenEng));

        assertEquals(2.0d, first.getScore());
        assertEquals(70.0d / 27.0d, second.getScore());
        assertEquals(0, first.getCritRate());
        assertEquals(0, second.getCritRate());
    }

    /**
     * Critical multipliers add their deltas to the initial one-times multiplier;
     * two doubled Eng effects therefore make three-times Eng excess.
     */
    @Test
    void criticalStatMultiplierDeltasAddAcrossCards() {
        SpecialAbility multiplier = RuleType.MultiplyCriticalRate.rewardMultiplyCritRate(2, 1, 1);
        Ship alpha = ship("Alpha", Role.Eng, 10, 0, 0, multiplier);
        Ship beta = ship("Beta", Role.Eng, 10, 0, 0, multiplier);
        Admiral admiral = admiral(alpha, beta);
        admiral.addReusableShips(List.of(alpha, beta), RosterState.ACTIVE);
        Assignment assignment = admiral.getAssignment(0);
        require(assignment, 15, 0, 0);
        assignment.setTargetCritChance(25);
        RosterCard alphaCard = activeCard(admiral, alpha);
        RosterCard betaCard = activeCard(admiral, beta);

        AssignmentSolution solution = solutionFor(admiral, betaCard, alphaCard);

        assertEquals(20, solution.getEng());
        assertEquals(15, solution.getCritRate());
        assertEquals(5.0d / 15.0d, solution.getScore());
    }

    /**
     * The Event multiplier also adds deltas, then one final rounding occurs before
     * comparing the displayed critical rating with the target.
     */
    @Test
    void eventCriticalMultiplierDeltasAndFinalRoundingShapeTheScore() {
        Ship ship = ship("Critical Event", Role.Eng, 10, 0, 0,
                new And(
                        RuleType.MultiplyCriticalRate.rewardMultiplyEventCritRate(1.5),
                        RuleType.MultiplyCriticalRate.rewardMultiplyEventCritRate(1.25)));
        Admiral admiral = admiral(ship);
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        Assignment assignment = admiral.getAssignment(0);
        require(assignment, 10, 0, 0);
        assignment.setEventCritRate(7);
        assignment.setTargetCritChance(20);

        AssignmentSolution solution = solutionFor(admiral, activeCard(admiral, ship));

        assertEquals(12, solution.getCritRate());
        assertEquals(0.7d, solution.getScore());

        assignment.setEventCritRate(2);
        AssignmentSolution halfStep = solutionFor(admiral, activeCard(admiral, ship));
        assertEquals(4, halfStep.getCritRate());
        assertEquals(0.1d, halfStep.getScore());
    }

    /** Compound children run in pair, Assignment, and critical phases. */
    @Test
    void compoundAbilityAppliesEachChildInItsScoringPhase() {
        Ship source = ship("Compound", Role.Eng, 10, 0, 0,
                new And(RuleType.PerTac.rewardEng(5),
                        new And(RuleType.All.rewardTac(3),
                                RuleType.MultiplyCriticalRate.rewardMultiplyCritRate(2, 1, 1))));
        Ship companion = ship("Companion", Role.Tac, 0, 0, 0, RuleType.All.rewardNothing());
        Admiral admiral = admiral(source, companion);
        admiral.addReusableShips(List.of(source, companion), RosterState.ACTIVE);
        require(admiral.getAssignment(0), 10, 3, 0);
        RosterCard sourceCard = activeCard(admiral, source);
        RosterCard companionCard = activeCard(admiral, companion);

        AssignmentSolution solution = solutionFor(admiral, companionCard, sourceCard);

        assertEquals(15, solution.getEng());
        assertEquals(3, solution.getTac());
        assertEquals(10, solution.getCritRate());
        assertEquals(10.0d / 13.0d, solution.getScore());
    }

    /** The currently stored critical-rating reward has no scoring effect. */
    @Test
    void criticalRatingRewardLeavesDisplayedRatingAndScoreUnchanged() {
        Ship ship = ship("Dormant Rating", Role.Eng, 10, 0, 0,
                RuleType.All.rewardCritRating(100));
        Admiral admiral = admiral(ship);
        admiral.addReusableShips(List.of(ship), RosterState.ACTIVE);
        require(admiral.getAssignment(0), 10, 0, 0);

        AssignmentSolution solution = solutionFor(admiral, activeCard(admiral, ship));

        assertEquals(0, solution.getCritRate());
        assertEquals(0.0d, solution.getScore());
    }

    /**
     * Equal base scores preserve natural Ship order and a lower score outranks a
     * lower-index multi-card choice for one, two, and three Assignments.
     */
    @Test
    void oneTwoAndThreeAssignmentsKeepScoreThenNaturalIndexOrder() {
        Ship earlyTier = ship("Zulu", Tier.Tier5, Role.Eng, 10, 10, 10,
                RuleType.All.rewardNothing());
        Ship alpha = ship("Alpha", Role.Eng, 10, 10, 10,
                RuleType.All.rewardNothing());
        Ship beta = ship("Beta", Role.Eng, 10, 10, 10,
                RuleType.All.rewardNothing());
        Admiral admiral = admiral(beta, alpha, earlyTier);
        admiral.addReusableShips(List.of(beta, alpha, earlyTier), RosterState.ACTIVE);
        for (int index = 0; index < 3; index++) {
            require(admiral.getAssignment(index), 10, 10, 10);
        }
        RosterCard zuluCard = activeCard(admiral, earlyTier);
        RosterCard alphaCard = activeCard(admiral, alpha);
        RosterCard betaCard = activeCard(admiral, beta);

        List<CompositeSolution> one = admiral.solveAssignments();
        assertSingleCardPerAssignment(one.get(0), zuluCard);
        assertSingleCardPerAssignment(one.get(1), alphaCard);
        assertSingleCardPerAssignment(one.get(2), betaCard);
        assertEquals(0.0d, one.get(0).getScore());
        assertEquals(0.0d, one.get(1).getScore());
        assertEquals(0.0d, one.get(2).getScore());
        assertEquals(1.0d, one.get(3).getScore());

        admiral.setAssignmentCount(2);
        List<CompositeSolution> two = admiral.solveAssignments();
        assertSingleCardPerAssignment(two.get(0), zuluCard, alphaCard);
        assertSingleCardPerAssignment(two.get(1), zuluCard, betaCard);
        assertSingleCardPerAssignment(two.get(2), alphaCard, betaCard);
        assertEquals(0.0d, two.get(0).getScore());

        admiral.setAssignmentCount(3);
        CompositeSolution three = admiral.solveAssignments().getFirst();
        assertSingleCardPerAssignment(three, zuluCard, alphaCard, betaCard);
        assertEquals(0.0d, three.getScore());
    }

    /**
     * One-Time copies and the reusable card of one Ship keep separate identities;
     * reversing group priority changes the exact cards selected first.
     */
    @Test
    void cardPriorityKeepsReusableAndOneTimeCopiesDistinctAcrossAssignments() {
        Ship shared = ship("Shared", Role.Eng, 10, 10, 10,
                RuleType.All.rewardNothing());
        Admiral admiral = admiral(shared);
        admiral.addReusableShips(List.of(shared), RosterState.ACTIVE);
        admiral.adjustOneTimeShipQuantity(shared, 2);
        for (int index = 0; index < 3; index++) {
            require(admiral.getAssignment(index), 10, 10, 10);
        }
        RosterCard reusable = activeCard(admiral, shared);
        RosterCard firstCopy = admiral.getRoster().getOneTimeCards().get(0);
        RosterCard secondCopy = admiral.getRoster().getOneTimeCards().get(1);
        assertNotEquals(reusable.getId(), firstCopy.getId());
        assertNotEquals(firstCopy.getId(), secondCopy.getId());
        assertSame(shared, firstCopy.getShip());
        assertSame(shared, secondCopy.getShip());

        for (int count = 1; count <= 3; count++) {
            admiral.setAssignmentCount(count);
            RosterCard[] expected = {reusable, firstCopy, secondCopy};
            assertSingleCardPerAssignment(admiral.solveAssignments().getFirst(),
                    Arrays.copyOf(expected, count));
        }

        admiral.setPrioritizeActive(false);
        for (int count = 1; count <= 3; count++) {
            admiral.setAssignmentCount(count);
            RosterCard[] expected = {firstCopy, secondCopy, reusable};
            assertSingleCardPerAssignment(admiral.solveAssignments().getFirst(),
                    Arrays.copyOf(expected, count));
        }
    }

    /**
     * With a zero requirement total, NaN ranks before Infinity when its card is
     * earlier in natural order because both scores compare as an index tie.
     */
    @Test
    void zeroTotalRequirementsRetainNanInfinityAndIndexRanking() {
        Ship zero = ship("Alpha Zero", Role.Eng, 0, 0, 0,
                RuleType.All.rewardNothing());
        Ship positive = ship("Zulu Positive", Role.Eng, 10, 10, 10,
                RuleType.All.rewardNothing());
        Admiral admiral = admiral(positive, zero);
        admiral.addReusableShips(List.of(positive, zero), RosterState.ACTIVE);
        RosterCard zeroCard = activeCard(admiral, zero);
        RosterCard positiveCard = activeCard(admiral, positive);

        List<CompositeSolution> solutions = admiral.solveAssignments();

        assertEquals(3, solutions.size());
        assertSingleCardPerAssignment(solutions.get(0), zeroCard);
        assertTrue(Double.isNaN(solutions.get(0).getSolution(0).getScore()));
        assertTrue(Double.isNaN(solutions.get(0).getScore()));
        assertSingleCardPerAssignment(solutions.get(1), positiveCard);
        assertEquals(Double.POSITIVE_INFINITY, solutions.get(1).getScore());
        assertCards(solutions.get(2).getSolution(0), positiveCard, zeroCard);
        assertEquals(Double.POSITIVE_INFINITY, solutions.get(2).getScore());
    }
}
