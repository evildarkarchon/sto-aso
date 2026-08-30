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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CompositeSolution implements HasScore {

    protected AssignmentSolution[] solutions;
    protected double score;
    protected long planningRevision;

    /**
     * Combines Assignment Solutions calculated for the same Admiral planning revision.
     *
     * @param solutions one to three Assignment Solutions
     * @throws IllegalArgumentException if the Solutions were calculated for different planning revisions
     * @throws NullPointerException if {@code solutions} or one of its elements is null
     */
    public CompositeSolution(AssignmentSolution... solutions) {
        Objects.requireNonNull(solutions, "solutions");
        this.solutions = solutions.clone();
        planningRevision = this.solutions.length == 0 ? 0L : Objects.requireNonNull(
                this.solutions[0],
                "solutions contains null").getPlanningRevision();
        for (AssignmentSolution solution : this.solutions) {
            Objects.requireNonNull(solution, "solutions contains null");
            if (solution.getPlanningRevision() != planningRevision) {
                throw new IllegalArgumentException("Composite Solutions must share one planning revision");
            }
            score += solution.getScore();
        }
    }

    /**
     * Resolves every child Solution's selected indexes to the exact Roster-card candidates.
     *
     * @param cards Roster-card candidates supplied to Solver in their original order
     */
    void setRosterCards(List<RosterCard> cards) {
        for (AssignmentSolution solution : solutions) {
            solution.setRosterCards(cards);
        }
    }

    @Override
    public double getScore() {
        return score;
    }

    /**
     * Returns the child Assignment Solutions without exposing structural array mutation.
     *
     * @return a shallow copy in Assignment order
     */
    public AssignmentSolution[] getSolutions() {
        return solutions.clone();
    }

    public AssignmentSolution getSolution(int index) {
        if (index < 0) return null;
        if (index >= solutions.length) return null;
        return solutions[index];
    }

    /**
     * Returns every exact selected Roster card across the covered Assignments.
     *
     * @return immutable selected-card list in Assignment and slot order
     */
    public List<RosterCard> getRosterCards() {
        List<RosterCard> rosterCards = new ArrayList<RosterCard>();
        for (AssignmentSolution solution : solutions) {
            for (RosterCard rosterCard : solution.getRosterCards()) {
                if (rosterCard != null) {
                    rosterCards.add(rosterCard);
                }
            }
        }
        return Collections.unmodifiableList(rosterCards);
    }

    /**
     * Returns the Admiral planning revision shared by every child Solution.
     *
     * @return captured planning revision
     */
    public long getPlanningRevision() {
        return planningRevision;
    }

    /**
     * Verifies every child Solution has exact Roster identities for all selected slots.
     *
     * @return {@code true} when deployment can validate the full composite selection
     * @throws NullPointerException if caller mutation inserted a null child Solution
     */
    boolean hasCompleteRosterCardSelection() {
        for (AssignmentSolution solution : solutions) {
            AssignmentSolution child = Objects.requireNonNull(solution, "solutions contains null");
            if (child.getPlanningRevision() != planningRevision
                    || !child.hasCompleteRosterCardSelection()) {
                return false;
            }
        }
        return true;
    }

    public int size() {
        return solutions.length;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CompositeSolution[").append(score).append("](\n");
        for (AssignmentSolution solution : solutions) {
            sb.append("\t").append(solution).append("\n");
        }
        sb.append(")");
        return sb.toString();
    }

}
