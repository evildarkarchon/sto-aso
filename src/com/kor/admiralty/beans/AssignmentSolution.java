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

import java.util.Arrays;

/** Frozen scored values and exact Roster-card slots for one Assignment. */
public final class AssignmentSolution implements HasScore {

    private final RosterCard[] rosterCards;
    private final long planningRevision;
    private final int eng;
    private final int tac;
    private final int sci;
    private final int eventCritRate;
    private final int critRate;
    private final double score;

    /**
     * Captures values and exact card references computed by Solver for one
     * Assignment. The slot array is copied so later caller edits cannot alter it.
     *
     * @param eventCritRate    captured Event critical rate
     * @param planningRevision Admiral planning revision represented by the candidate
     * @param eng              final engineering total
     * @param tac              final tactical total
     * @param sci              final science total
     * @param critRate         final rounded critical rating
     * @param score            final score, including existing non-finite outcomes
     * @param rosterCards      selected cards in slot order, with null empty slots
     */
    AssignmentSolution(int eventCritRate, long planningRevision, int eng, int tac,
                       int sci, int critRate, double score, RosterCard[] rosterCards) {
        this.eventCritRate = eventCritRate;
        this.planningRevision = planningRevision;
        this.rosterCards = rosterCards.clone();
        this.eng = eng;
        this.tac = tac;
        this.sci = sci;
        this.critRate = critRate;
        this.score = score;
    }

    public int getEventCritRate() {
        return eventCritRate;
    }

    /**
     * Returns the exact selected Roster cards in assignment slot order.
     * Empty slots contain {@code null}; the returned array may be modified without
     * changing the Solution.
     *
     * @return selected identity-bearing cards in slot order
     */
    public RosterCard[] getRosterCards() {
        return rosterCards.clone();
    }

    /**
     * Returns the Admiral planning revision for which this Solution was calculated.
     *
     * @return captured planning revision
     */
    public long getPlanningRevision() {
        return planningRevision;
    }

    /**
     * Verifies the selected cards occupy a nonempty prefix of the three slots.
     *
     * @return {@code true} when the Solution carries a complete identity-bearing
     * selection
     */
    boolean hasCompleteRosterCardSelection() {
        if (rosterCards.length != 3 || rosterCards[0] == null) {
            return false;
        }
        for (int slot = 1; slot < rosterCards.length; slot++) {
            if (rosterCards[slot] != null && rosterCards[slot - 1] == null) {
                return false;
            }
        }
        return true;
    }

    public int getEng() {
        return eng;
    }

    public int getTac() {
        return tac;
    }

    public int getSci() {
        return sci;
    }

    public int getCritRate() {
        return critRate;
    }

    @Override
    public double getScore() {
        return score;
    }

    /** Describes the captured card slots and score without exposing candidate indexes. */
    @Override
    public String toString() {
        return "Solution" + Arrays.toString(rosterCards) + " = " + score;
    }

}
