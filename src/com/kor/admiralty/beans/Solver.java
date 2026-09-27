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

    private static final Comparator<AssignmentCandidate> ASSIGNMENT_COMPARATOR = (left,
                                                                                  right) -> compareAssignmentCandidates(left, right);
    private static final Comparator<HasScore> COMPARATOR = new ScoreComparator();
    private static final Comparator<CompositeCandidate> COMPOSITE_COMPARATOR = (left,
                                                                                right) -> compareCompositeCandidates(left, right);
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
        List<CompositeCandidate> candidates = solveCanonicalShips(
                assignments,
                ships,
                numSolutions,
                planningRevision);
        List<CompositeSolution> solutions = new ArrayList<CompositeSolution>(candidates.size());
        for (CompositeCandidate candidate : candidates) {
            // Only retained choices resolve indexes; discarded candidates never
            // acquire or expose Roster-card references.
            solutions.add(candidate.toSolution(rosterCards));
        }
        return solutions;
    }

    /**
     * Computes composite candidates from canonical Ship facts and stamps every child
     * with one planning revision.
     *
     * @param assignments      ordered current Assignments, up to three
     * @param ships            canonical Ship facts in candidate order
     * @param numSolutions     maximum number of composite candidates to retain
     * @param planningRevision planning revision represented by the inputs
     * @return best composite candidates, retaining indexes only inside Solver
     */
    private static List<CompositeCandidate> solveCanonicalShips(
            List<Assignment> assignments,
            List<Ship> ships,
            int numSolutions,
            long planningRevision) {
        Assignment assignment1 = assignments.size() > 0 ? assignments.get(0) : null;
        Assignment assignment2 = assignments.size() > 1 ? assignments.get(1) : null;
        Assignment assignment3 = assignments.size() > 2 ? assignments.get(2) : null;
        List<AssignmentCandidate> candidates1 = solveAssignment(assignment1, ships, numSolutions, planningRevision);
        List<AssignmentCandidate> candidates2 = solveAssignment(assignment2, ships, numSolutions, planningRevision);
        List<AssignmentCandidate> candidates3 = solveAssignment(assignment3, ships, numSolutions, planningRevision);

        TreeSet<CompositeCandidate> candidates = new TreeSet<CompositeCandidate>(COMPOSITE_COMPARATOR);
        for (int index1 = 0; index1 < candidates1.size(); index1++) {
            AssignmentCandidate candidate1 = candidates1.get(index1);

            if (candidates2.isEmpty()) {
                CompositeCandidate candidate = new CompositeCandidate(candidate1);
                candidates.add(candidate);
            } else {
                for (int index2 = index1; index2 < candidates2.size(); index2++) {
                    AssignmentCandidate candidate2 = candidates2.get(index2);

                    if (candidates3.isEmpty()) {
                        if (isValid(candidate1, candidate2)) {
                            CompositeCandidate candidate = new CompositeCandidate(candidate1, candidate2);
                            candidates.add(candidate);
                        }
                    } else {
                        for (int index3 = index2; index3 < candidates3.size(); index3++) {
                            AssignmentCandidate candidate3 = candidates3.get(index3);
                            if (isValid(candidate1, candidate2, candidate3)) {
                                CompositeCandidate candidate = new CompositeCandidate(candidate1, candidate2, candidate3);
                                candidates.add(candidate);
                            }
                        }
                    }
                }
            }
        }

        return getTopCandidates(candidates, numSolutions);
    }

    /**
     * Checks whether candidate selections use distinct Roster-card indexes.
     *
     * @param candidates ordered Assignment candidates in one composite choice
     * @return whether no selected index occurs twice
     */
    private static boolean isValid(AssignmentCandidate... candidates) {
        BitSet ships = new BitSet();
        for (AssignmentCandidate candidate : candidates) {
            if (candidate == null)
                continue;

            int[] indexes = candidate.shipIndexes;
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

    /**
     * Returns the requested prefix of ordered candidates after ranking completes.
     *
     * @param candidates scored candidates in best-first order
     * @param numSolutions maximum number to return
     * @return ordered candidates retained for publication or combination
     */
    private static <S extends HasScore> List<S> getTopCandidates(SortedSet<S> candidates, int numSolutions) {
        int num = Math.min(numSolutions, candidates.size());
        List<S> top = new ArrayList<S>(candidates);
        candidates.clear();
        return top.subList(0, num);
    }

    /**
     * Solves one Assignment and stamps its candidates with the supplied Admiral
     * planning revision. Retains only the requested best candidates during enumeration.
     *
     * @param assignment       current Assignment, or {@code null}
     * @param ships            canonical Ship facts in candidate order
     * @param numSolutions     maximum number of Assignment candidates to retain
     * @param planningRevision planning revision represented by the inputs
     * @return best Assignment candidates, or an empty list for no Assignment
     */
    private static List<AssignmentCandidate> solveAssignment(
            Assignment assignment,
            List<Ship> ships,
            int numSolutions,
            long planningRevision) {
        if (assignment == null)
            return Collections.emptyList();

        int numShips = ships.size();
        TreeSet<AssignmentCandidate> candidates = new TreeSet<AssignmentCandidate>(ASSIGNMENT_COMPARATOR);
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
                    AssignmentCandidate candidate = computeAssignmentCandidate(
                            assignment,
                            ships,
                            planningRevision,
                            slot1,
                            slot2,
                            slot3);
                    candidates.add(candidate);
                    // Index ties distinguish every card combination, so discard the
                    // worst immediately to keep retained memory bounded by the limit.
                    if (candidates.size() > numSolutions) {
                        candidates.pollLast();
                    }
                }
            }
        }
        return getTopCandidates(candidates, numSolutions);
    }

    /**
     * Orders Assignment candidates by score, then by stable candidate indexes when
     * scores tie.
     * The tie-break retains identity-distinct cards without disturbing natural or
     * priority candidate order.
     *
     * @param left  first candidate
     * @param right second candidate
     * @return comparator result
     */
    private static int compareAssignmentCandidates(AssignmentCandidate left, AssignmentCandidate right) {
        int scoreComparison = COMPARATOR.compare(left, right);
        if (scoreComparison != 0) {
            return scoreComparison;
        }
        return compareIndexes(left.shipIndexes, right.shipIndexes);
    }

    /**
     * Orders composite candidates by score, then by their child candidate indexes
     * when scores tie.
     *
     * @param left  first composite candidate
     * @param right second composite candidate
     * @return comparator result
     */
    private static int compareCompositeCandidates(CompositeCandidate left, CompositeCandidate right) {
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
                    left.getCandidate(index).shipIndexes,
                    right.getCandidate(index).shipIndexes);
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
     * Computes one scored Assignment candidate from canonical Ship facts for a
     * planning revision.
     *
     * @param assignment       Assignment whose requirements determine the score
     * @param ships            canonical Ship facts in candidate order
     * @param planningRevision planning revision represented by the inputs
     * @param index1           first selected candidate index, or {@code -1}
     * @param index2           second selected candidate index, or {@code -1}
     * @param index3           third selected candidate index, or {@code -1}
     * @return scored candidate retaining the selected indexes inside Solver
     */
    private static AssignmentCandidate computeAssignmentCandidate(
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
         * retains only the candidate's calculated values and selected indexes.
         *
         * @param assignment       Assignment whose requirements determine the score
         * @param planningRevision Admiral planning revision represented by the candidate
         * @param indexes          selected candidate indexes in slot order
         * @return scored Assignment candidate
         */
        private AssignmentCandidate finish(Assignment assignment, long planningRevision, int... indexes) {
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
            return new AssignmentCandidate(eventCritRate, planningRevision, eng, tac, sci,
                    critRate, score, indexes);
        }
    }

    /** Scored selection whose candidate indexes never leave Solver. */
    private static final class AssignmentCandidate implements HasScore {
        private final int eventCritRate;
        private final long planningRevision;
        private final int eng;
        private final int tac;
        private final int sci;
        private final int critRate;
        private final double score;
        private final int[] shipIndexes;

        /**
         * Keeps calculated values and Solver-owned slot indexes for ranking.
         *
         * @param eventCritRate Event critical rate before multiplier effects
         * @param planningRevision Admiral revision captured for this choice
         * @param eng final engineering total
         * @param tac final tactical total
         * @param sci final science total
         * @param critRate final rounded critical rating
         * @param score final score, including NaN or Infinity for zero requirements
         * @param shipIndexes selected candidate indexes in slot order; owned by Solver
         */
        private AssignmentCandidate(int eventCritRate, long planningRevision, int eng, int tac,
                                    int sci, int critRate, double score, int... shipIndexes) {
            this.eventCritRate = eventCritRate;
            this.planningRevision = planningRevision;
            this.eng = eng;
            this.tac = tac;
            this.sci = sci;
            this.critRate = critRate;
            this.score = score;
            // Each calculation creates this varargs array; it stays inside Solver.
            this.shipIndexes = shipIndexes;
        }

        @Override
        public double getScore() {
            return score;
        }

        /**
         * Resolves a retained candidate to original Roster-card objects.
         *
         * @param rosterCards cards in the same order used during candidate enumeration
         * @return immutable scored Solution with exact selected references
         */
        private AssignmentSolution toSolution(List<RosterCard> rosterCards) {
            RosterCard[] selectedCards = new RosterCard[shipIndexes.length];
            for (int slot = 0; slot < shipIndexes.length; slot++) {
                if (shipIndexes[slot] >= 0) {
                    selectedCards[slot] = rosterCards.get(shipIndexes[slot]);
                }
            }
            return new AssignmentSolution(eventCritRate, planningRevision, eng, tac, sci,
                    critRate, score, selectedCards);
        }
    }

    /** Ordered Assignment candidates and their score before public publication. */
    private static final class CompositeCandidate implements HasScore {
        private final AssignmentCandidate[] candidates;
        private final double score;

        /**
         * Sums child scores in their existing Assignment order.
         *
         * @param candidates ordered Assignment candidates for this combination
         */
        private CompositeCandidate(AssignmentCandidate... candidates) {
            // Combination calls supply a fresh varargs array that never leaves Solver.
            this.candidates = candidates;
            double total = 0d;
            for (AssignmentCandidate candidate : candidates) {
                total += candidate.getScore();
            }
            score = total;
        }

        @Override
        public double getScore() {
            return score;
        }

        /** Returns the number of ordered Assignment candidates. */
        private int size() {
            return candidates.length;
        }

        /** Returns the candidate at an Assignment position. */
        private AssignmentCandidate getCandidate(int index) {
            return candidates[index];
        }

        /**
         * Publishes retained children using exact cards from the input Roster.
         *
         * @param rosterCards cards in the same order used during candidate enumeration
         * @return composite Solution with frozen per-Assignment children
         */
        private CompositeSolution toSolution(List<RosterCard> rosterCards) {
            AssignmentSolution[] published = new AssignmentSolution[candidates.length];
            for (int index = 0; index < candidates.length; index++) {
                published[index] = candidates[index].toSolution(rosterCards);
            }
            return new CompositeSolution(published);
        }
    }

}
