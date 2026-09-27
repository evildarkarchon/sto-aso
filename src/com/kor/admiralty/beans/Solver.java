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
package com.kor.admiralty.beans;

import java.util.*;

final class Solver {

    static final Comparator<AssignmentSolution> ASSIGNMENT_COMPARATOR = (left,
                                                                         right) -> compareAssignmentSolutions(left, right);
    private static final Comparator<HasScore> COMPARATOR = new ScoreComparator();
    private static final Comparator<CompositeSolution> COMPOSITE_COMPARATOR = (left,
                                                                               right) -> compareCompositeSolutions(left, right);
    //private static final double WEIGHT_POSITIVE = 1.0d;
    //private static final double WEIGHT_NEGATIVE = 3.0d;

    /**
     * Solves Assignments against exact Roster-card candidates while scoring their
     * canonical Ship facts.
     *
     * @param assignments      ordered current Assignments, up to three
     * @param rosterCards      deployable cards from one immutable Roster view
     * @param numSolutions     maximum number of composite Solutions to retain
     * @param planningRevision Admiral planning revision represented by the inputs
     * @return best composite Solutions with exact selected card identities attached
     * @throws IllegalArgumentException if more than three Assignments are supplied,
     *                                  the revision is negative, or a card identity repeats
     * @throws NullPointerException     if an input list or one of its cards is null
     */
    static List<CompositeSolution> solve(
            List<Assignment> assignments,
            List<RosterCard> rosterCards,
            int numSolutions,
            long planningRevision) {
        Objects.requireNonNull(assignments, "assignments");
        Objects.requireNonNull(rosterCards, "rosterCards");
        if (assignments.size() > 3) {
            throw new IllegalArgumentException("At most three Assignments can be solved");
        }
        if (planningRevision < 0L) {
            throw new IllegalArgumentException("Planning revision must be non-negative");
        }
        List<Ship> ships = new ArrayList<Ship>(rosterCards.size());
        Set<RosterCardId> cardIds = new HashSet<RosterCardId>();
        for (RosterCard rosterCard : rosterCards) {
            Objects.requireNonNull(rosterCard, "rosterCards contains null");
            // Solver uses candidate indexes for collision checks, so each index must
            // represent one unique card.
            if (!cardIds.add(rosterCard.getId())) {
                throw new IllegalArgumentException("Roster-card identity appears more than once");
            }
            ships.add(rosterCard.getShip());
        }
        List<CompositeSolution> solutions = solveCanonicalShips(
                assignments,
                ships,
                numSolutions,
                planningRevision);
        for (CompositeSolution solution : solutions) {
            solution.setRosterCards(rosterCards);
        }
        return solutions;
    }

    /**
     * Computes composite Solutions from canonical Ship facts and stamps every child
     * with one planning revision.
     *
     * @param assignments      ordered current Assignments, up to three
     * @param ships            canonical Ship facts in candidate order
     * @param numSolutions     maximum number of composite Solutions to retain
     * @param planningRevision planning revision represented by the inputs
     * @return best composite Solutions without their candidate values attached
     */
    private static List<CompositeSolution> solveCanonicalShips(
            List<Assignment> assignments,
            List<Ship> ships,
            int numSolutions,
            long planningRevision) {
        Assignment assignment1 = assignments.size() > 0 ? assignments.get(0) : null;
        Assignment assignment2 = assignments.size() > 1 ? assignments.get(1) : null;
        Assignment assignment3 = assignments.size() > 2 ? assignments.get(2) : null;
        List<AssignmentSolution> solutions1 = solveAssignment(assignment1, ships, numSolutions, planningRevision);
        List<AssignmentSolution> solutions2 = solveAssignment(assignment2, ships, numSolutions, planningRevision);
        List<AssignmentSolution> solutions3 = solveAssignment(assignment3, ships, numSolutions, planningRevision);

        TreeSet<CompositeSolution> solutions = new TreeSet<CompositeSolution>(COMPOSITE_COMPARATOR);
        for (int index1 = 0; index1 < solutions1.size(); index1++) {
            AssignmentSolution solution1 = solutions1.get(index1);

            if (solutions2.isEmpty()) {
                CompositeSolution solution = new CompositeSolution(solution1);
                solutions.add(solution);
            } else {
                for (int index2 = index1; index2 < solutions2.size(); index2++) {
                    AssignmentSolution solution2 = solutions2.get(index2);

                    if (solutions3.isEmpty()) {
                        if (isValid(solution1, solution2)) {
                            CompositeSolution solution = new CompositeSolution(solution1, solution2);
                            solutions.add(solution);
                        }
                    } else {
                        for (int index3 = index2; index3 < solutions3.size(); index3++) {
                            AssignmentSolution solution3 = solutions3.get(index3);
                            if (isValid(solution1, solution2, solution3)) {
                                CompositeSolution solution = new CompositeSolution(solution1, solution2, solution3);
                                solutions.add(solution);
                            }
                        }
                    }
                }
            }
        }

        return getTopSolutions(solutions, numSolutions);
    }

    private static boolean isValid(AssignmentSolution... solutions) {
        BitSet ships = new BitSet();
        for (AssignmentSolution solution : solutions) {
            if (solution == null)
                continue;

            int[] indexes = solution.getShipIndexes();
            for (int index : indexes) {
                if (index < 0)
                    continue;

                if (ships.get(index)) {
                    // This ship has already been used!
                    return false;
                }
                ships.set(index);
            }
        }
        return true;
    }

    private static <S extends HasScore> List<S> getTopSolutions(SortedSet<S> solutions, int numSolutions) {
        int num = Math.min(numSolutions, solutions.size());
        List<S> top = new ArrayList<S>(solutions);
        solutions.clear();
        return top.subList(0, num);
    }

    /**
     * Solves one Assignment and stamps its candidates with the supplied Admiral
     * planning revision. Retains only the requested best candidates during enumeration.
     *
     * @param assignment       current Assignment, or {@code null}
     * @param ships            canonical Ship facts in candidate order
     * @param numSolutions     maximum number of Assignment Solutions to retain
     * @param planningRevision planning revision represented by the inputs
     * @return best Assignment Solutions, or an empty list for no Assignment
     */
    private static List<AssignmentSolution> solveAssignment(
            Assignment assignment,
            List<Ship> ships,
            int numSolutions,
            long planningRevision) {
        if (assignment == null)
            return Collections.emptyList();

        int numShips = ships.size();
        TreeSet<AssignmentSolution> solutions = new TreeSet<AssignmentSolution>(ASSIGNMENT_COMPARATOR);
        for (int slot3 = -1; slot3 < numShips; slot3++) {
            // Ship ship3 = slot3 < 0 ? null : ships.get(slot3);
            for (int slot2 = -1; slot2 < numShips; slot2++) {
                if ((slot2 <= slot3) && (slot3 != -1))
                    continue;
                // Ship ship2 = slot2 < 0 ? null : ships.get(slot2);
                for (int slot1 = -1; slot1 < numShips; slot1++) {
                    if ((slot1 <= slot2))
                        continue;
                    // Ship ship1 = slot1 < 0 ? null : ships.get(slot1);
                    AssignmentSolution solution = computeAssignmentSolution(
                            assignment,
                            ships,
                            planningRevision,
                            slot1,
                            slot2,
                            slot3);
                    solutions.add(solution);
                    // Index ties distinguish every card combination, so discard the
                    // worst immediately to keep retained memory bounded by the limit.
                    if (solutions.size() > numSolutions) {
                        solutions.pollLast();
                    }
                }
            }
        }
        return getTopSolutions(solutions, numSolutions);
    }

    /**
     * Orders Assignment Solutions by score, then by stable candidate indexes when
     * scores tie.
     * The tie-break retains identity-distinct cards without disturbing natural or
     * priority candidate order.
     *
     * @param left  first Solution
     * @param right second Solution
     * @return comparator result
     */
    private static int compareAssignmentSolutions(AssignmentSolution left, AssignmentSolution right) {
        int scoreComparison = COMPARATOR.compare(left, right);
        if (scoreComparison != 0) {
            return scoreComparison;
        }
        return compareIndexes(left.getShipIndexes(), right.getShipIndexes());
    }

    /**
     * Orders composite Solutions by score, then by their child candidate indexes
     * when scores tie.
     *
     * @param left  first composite Solution
     * @param right second composite Solution
     * @return comparator result
     */
    private static int compareCompositeSolutions(CompositeSolution left, CompositeSolution right) {
        int scoreComparison = COMPARATOR.compare(left, right);
        if (scoreComparison != 0) {
            return scoreComparison;
        }
        int sizeComparison = Integer.compare(left.size(), right.size());
        if (sizeComparison != 0) {
            return sizeComparison;
        }
        for (int index = 0; index < left.size(); index++) {
            int selectionComparison = compareIndexes(
                    left.getSolution(index).getShipIndexes(),
                    right.getSolution(index).getShipIndexes());
            if (selectionComparison != 0) {
                return selectionComparison;
            }
        }
        return 0;
    }

    /**
     * Compares two fixed-size candidate-index selections lexicographically.
     *
     * @param left  first candidate indexes
     * @param right second candidate indexes
     * @return comparator result
     */
    private static int compareIndexes(int[] left, int[] right) {
        int lengthComparison = Integer.compare(left.length, right.length);
        if (lengthComparison != 0) {
            return lengthComparison;
        }
        for (int index = 0; index < left.length; index++) {
            int valueComparison = Integer.compare(left[index], right[index]);
            if (valueComparison != 0) {
                return valueComparison;
            }
        }
        return 0;
    }

    /**
     * Computes one scored Assignment Solution from canonical Ship facts for a
     * planning revision.
     *
     * @param assignment       Assignment whose requirements determine the score
     * @param ships            canonical Ship facts in candidate order
     * @param planningRevision planning revision represented by the inputs
     * @param index1           first selected candidate index, or {@code -1}
     * @param index2           second selected candidate index, or {@code -1}
     * @param index3           third selected candidate index, or {@code -1}
     * @return scored Assignment Solution retaining the selected candidate indexes
     */
    private static AssignmentSolution computeAssignmentSolution(
            Assignment assignment,
            List<Ship> ships,
            long planningRevision,
            int index1,
            int index2,
            int index3) {
        Ship ship1 = index1 >= 0 ? ships.get(index1) : null;
        Ship ship2 = index2 >= 0 ? ships.get(index2) : null;
        Ship ship3 = index3 >= 0 ? ships.get(index3) : null;
        CalculationState calculation = new CalculationState(assignment.getEventCritRate(),
                (index1 < 0 ? 0 : 1) + (index2 < 0 ? 0 : 1) + (index3 < 0 ? 0 : 1));
        // Keep each source's base stats ahead of its pair effects, visiting empty
        // targets as well as occupied slots before the Assignment-wide phase.
        if (ship1 != null) {
            calculation.addStats(ship1.getEng(), ship1.getTac(), ship1.getSci());
            SpecialAbility ability = ship1.getSpecialAbility();
            ability.procShip(calculation, ship1, ship2);
            ability.procShip(calculation, ship1, ship3);
        }
        if (ship2 != null) {
            calculation.addStats(ship2.getEng(), ship2.getTac(), ship2.getSci());
            SpecialAbility ability = ship2.getSpecialAbility();
            ability.procShip(calculation, ship2, ship1);
            ability.procShip(calculation, ship2, ship3);
        }
        if (ship3 != null) {
            calculation.addStats(ship3.getEng(), ship3.getTac(), ship3.getSci());
            SpecialAbility ability = ship3.getSpecialAbility();
            ability.procShip(calculation, ship3, ship1);
            ability.procShip(calculation, ship3, ship2);
        }
        if (ship1 != null) {
            ship1.getSpecialAbility().procAssignment(calculation, assignment);
        }
        if (ship2 != null) {
            ship2.getSpecialAbility().procAssignment(calculation, assignment);
        }
        if (ship3 != null) {
            ship3.getSpecialAbility().procAssignment(calculation, assignment);
        }
        if (ship1 != null) {
            ship1.getSpecialAbility().procCriticals(calculation, assignment);
        }
        if (ship2 != null) {
            ship2.getSpecialAbility().procCriticals(calculation, assignment);
        }
        if (ship3 != null) {
            ship3.getSpecialAbility().procCriticals(calculation, assignment);
        }

        return calculation.finish(assignment, planningRevision, index1, index2, index3);
    }

    /** Mutable totals and effect flags for exactly one candidate calculation. */
    private static final class CalculationState implements ScoringEffects {
        private final int eventCritRate;
        private final int selectedShipCount;
        private int eng;
        private int tac;
        private int sci;
        private boolean ignoreEventEng;
        private boolean ignoreEventTac;
        private boolean ignoreEventSci;
        private double eventCritMultiplier = 1d;
        private double engCritMultiplier = 1d;
        private double tacCritMultiplier = 1d;
        private double sciCritMultiplier = 1d;

        /** Starts one candidate with its Event critical rate and occupied slot count. */
        private CalculationState(int eventCritRate, int selectedShipCount) {
            this.eventCritRate = eventCritRate;
            this.selectedShipCount = selectedShipCount;
        }

        /** Accumulates base Ship statistics or a reward's statistic effect. */
        @Override
        public void addStats(int eng, int tac, int sci) {
            this.eng += eng;
            this.tac += tac;
            this.sci += sci;
        }

        /** Replaces all three Event-ignore decisions with the latest reward. */
        @Override
        public void setIgnoredEventStats(boolean eng, boolean tac, boolean sci) {
            // A later reward replaces earlier flags even when it supplies false.
            ignoreEventEng = eng;
            ignoreEventTac = tac;
            ignoreEventSci = sci;
        }

        /** Adds reward deltas while retaining the one-times starting multipliers. */
        @Override
        public void addCriticalMultiplierDeltas(double event, double eng, double tac, double sci) {
            eventCritMultiplier += event;
            engCritMultiplier += eng;
            tacCritMultiplier += tac;
            sciCritMultiplier += sci;
        }

        /** Returns occupied slot count for the WhenAlone Special Ability. */
        @Override
        public int selectedShipCount() {
            return selectedShipCount;
        }

        /**
         * Applies the existing critical rounding and score arithmetic, then
         * publishes the candidate without exposing mutable calculation state.
         *
         * @param assignment       Assignment whose requirements determine the score
         * @param planningRevision Admiral planning revision represented by the candidate
         * @param indexes          selected candidate indexes in slot order
         * @return scored Assignment Solution
         */
        private AssignmentSolution finish(Assignment assignment, long planningRevision, int... indexes) {
            int assignmentEng = ignoreEventEng ? assignment.getRequiredEng() : assignment.eng();
            int assignmentTac = ignoreEventTac ? assignment.getRequiredTac() : assignment.tac();
            int assignmentSci = ignoreEventSci ? assignment.getRequiredSci() : assignment.sci();
            int assignmentCritRate = assignment.getTargetCritRate();
            int engDifference = eng - assignmentEng;
            int tacDifference = tac - assignmentTac;
            int sciDifference = sci - assignmentSci;
            // Preserve the single final round and int cast used by displayed ratings.
            int critRate = (int) Math.round(eventCritRate * eventCritMultiplier
                    + (engDifference > 0 ? engDifference : 0) * engCritMultiplier
                    + (tacDifference > 0 ? tacDifference : 0) * tacCritMultiplier
                    + (sciDifference > 0 ? sciDifference : 0) * sciCritMultiplier);
            int critDifference = critRate - assignmentCritRate;

            int absEng = Math.abs(engDifference);
            int absTac = Math.abs(tacDifference);
            int absSci = Math.abs(sciDifference);

            double score = 0d;
            double scoreEng = absEng * (engDifference > 0 ? 0d : 10d);
            double scoreTac = absTac * (tacDifference > 0 ? 0d : 10d);
            double scoreSci = absSci * (sciDifference > 0 ? 0d : 10d);
            double scoreCritRate = Math.abs(critDifference);
            // A zero requirement total deliberately retains NaN or Infinity.
            score = (scoreEng + scoreTac + scoreSci + scoreCritRate)
                    / (assignmentEng + assignmentTac + assignmentSci);
            return new AssignmentSolution(eventCritRate, planningRevision, eng, tac, sci,
                    critRate, score, indexes);
        }
    }

}
