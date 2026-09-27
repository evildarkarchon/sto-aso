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

public final class CompositeSolution implements HasScore {

    private final AssignmentSolution[] solutions;
    private final double score;
    private final long planningRevision;

    /**
     * Combines Assignment Solutions calculated for the same Admiral planning
     * revision.
     *
     * @param solutions ordered Assignment Solutions; deployment validates their count
     * @throws IllegalArgumentException if the Solutions were calculated for
     *                                  different planning revisions
     * @throws NullPointerException     if {@code solutions} or one of its elements
     *                                  is null
     */
    public CompositeSolution(AssignmentSolution... solutions) {
        Objects.requireNonNull(solutions, "solutions");
        this.solutions = solutions.clone();
        planningRevision = this.solutions.length == 0 ? 0L
                : Objects.requireNonNull(
                this.solutions[0],
                "solutions contains null").getPlanningRevision();
        double totalScore = 0d;
        for (AssignmentSolution solution : this.solutions) {
            Objects.requireNonNull(solution, "solutions contains null");
            if (solution.getPlanningRevision() != planningRevision) {
                throw new IllegalArgumentException("Composite Solutions must share one planning revision");
            }
            totalScore += solution.getScore();
        }
        score = totalScore;
    }

    @Override
    public double getScore() {
        return score;
    }

    /**
     * Returns the child Assignment Solutions without exposing structural array
     * mutation.
     *
     * @return a shallow copy in Assignment order
     */
    public AssignmentSolution[] getSolutions() {
        return solutions.clone();
    }

    public AssignmentSolution getSolution(int index) {
        if (index < 0)
            return null;
        if (index >= solutions.length)
            return null;
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
     * Verifies one to three child Solutions each have exact Roster identities for
     * all selected slots.
     *
     * @return {@code true} when deployment can validate the full composite
     * selection
     */
    boolean hasCompleteRosterCardSelection() {
        // The public constructor can represent malformed choices, so deployment
        // checks the Assignment count before any Roster mutation.
        if (solutions.length < 1 || solutions.length > 3) {
            return false;
        }
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
