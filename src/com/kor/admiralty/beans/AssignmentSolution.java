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
import java.util.List;

public class AssignmentSolution implements HasScore {

    protected int[] shipIndexes;
    protected RosterCard[] rosterCards;
    protected long planningRevision;
    protected int eng;
    protected int tac;
    protected int sci;
    protected int eventCritRate;
    protected int critRate;
    protected double score;

    /**
     * Creates a solution whose indexes will later resolve to exact Roster cards
     * from one planning revision.
     *
     * @param eventCritRate    event critical rate used for scoring
     * @param planningRevision Admiral planning revision captured before solving
     * @param shipIndexes      selected indexes in the supplied Roster-card
     *                         candidates
     */
    AssignmentSolution(int eventCritRate, long planningRevision, int... shipIndexes) {
        this(eventCritRate, planningRevision, 0, 0, 0, eventCritRate, 0d, shipIndexes);
    }

    /**
     * Publishes values computed by Solver for one Assignment candidate.
     *
     * @param eventCritRate    captured Event critical rate
     * @param planningRevision Admiral planning revision represented by the candidate
     * @param eng              final engineering total
     * @param tac              final tactical total
     * @param sci              final science total
     * @param critRate         final rounded critical rating
     * @param score            final score, including existing non-finite outcomes
     * @param shipIndexes      selected candidate indexes in slot order
     */
    AssignmentSolution(int eventCritRate, long planningRevision, int eng, int tac,
                       int sci, int critRate, double score, int... shipIndexes) {
        this.eventCritRate = eventCritRate;
        this.planningRevision = planningRevision;
        this.shipIndexes = shipIndexes;
        this.rosterCards = new RosterCard[shipIndexes.length];
        this.eng = eng;
        this.tac = tac;
        this.sci = sci;
        this.critRate = critRate;
        this.score = score;
    }

    public int getEventCritRate() {
        return eventCritRate;
    }

    public int[] getShipIndexes() {
        return shipIndexes;
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
     * Resolves selected candidate indexes to exact Roster cards.
     *
     * @param cards Roster-card candidates supplied to Solver in their original
     *              order
     */
    void setRosterCards(List<RosterCard> cards) {
        for (int i = 0; i < shipIndexes.length; i++) {
            if (shipIndexes[i] >= 0) {
                rosterCards[i] = cards.get(shipIndexes[i]);
            } else {
                rosterCards[i] = null;
            }
        }
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
     * Verifies every selected Solver slot resolved to an exact Roster card and
     * every empty slot stayed empty.
     *
     * @return {@code true} when the Solution carries a complete identity-bearing
     * selection
     */
    boolean hasCompleteRosterCardSelection() {
        if (shipIndexes.length != rosterCards.length) {
            return false;
        }
        for (int index = 0; index < shipIndexes.length; index++) {
            if ((shipIndexes[index] >= 0) == (rosterCards[index] == null)) {
                return false;
            }
        }
        return true;
    }

    public int getEng() {
        return eng;
    }

    public int addEng(int value) {
        this.eng += value;
        return eng;
    }

    public int getTac() {
        return tac;
    }

    public int addTac(int value) {
        this.tac += value;
        return tac;
    }

    public int getSci() {
        return sci;
    }

    public int addSci(int value) {
        this.sci += value;
        return sci;
    }

    public int getCritRate() {
        return critRate;
    }

    @Override
    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    @Override
    public String toString() {
        return "Solution" + Arrays.toString(shipIndexes) + " = " + score;
    }

}
