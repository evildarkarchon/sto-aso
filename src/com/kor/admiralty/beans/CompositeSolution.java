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

public class CompositeSolution implements HasScore {

    protected AssignmentSolution[] solutions;
    protected double score;
    protected long planningRevision;

    /**
     * Combines Assignment Solutions calculated for the same Admiral planning revision.
     *
     * @param solutions one to three Assignment Solutions
     * @throws IllegalArgumentException if the Solutions were calculated for different planning revisions
     */
    public CompositeSolution(AssignmentSolution... solutions) {
        this.solutions = solutions;
        planningRevision = solutions.length == 0 ? 0L : solutions[0].getPlanningRevision();
        for (AssignmentSolution solution : solutions) {
            if (solution.getPlanningRevision() != planningRevision) {
                throw new IllegalArgumentException("Composite Solutions must share one planning revision");
            }
            score += solution.getScore();
        }
    }

    @Override
    public void setShips(List<Ship> ships) {
        for (AssignmentSolution solution : solutions) {
            solution.setShips(ships);
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

    public AssignmentSolution[] getSolutions() {
        return solutions;
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
