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
package com.kor.admiralty.rewards;

import com.kor.admiralty.beans.Assignment;
import com.kor.admiralty.beans.AssignmentSolution;
import com.kor.admiralty.ui.resources.Strings;

public class RewardMultiplyCritRate implements Reward {

    protected double multiplyEng;
    protected double multiplyTac;
    protected double multiplySci;
    protected double multiplyBaseCritRate;

    public RewardMultiplyCritRate(double multiplyEng, double multiplyTac, double multiplySci,
                                  double multiplyBaseCritRate) {
        this.multiplyEng = multiplyEng;
        this.multiplyTac = multiplyTac;
        this.multiplySci = multiplySci;
        this.multiplyBaseCritRate = multiplyBaseCritRate;
    }

    @Override
    public void apply(Assignment assignment, AssignmentSolution solution) {
        solution.addEventCritMultiplier(multiplyBaseCritRate - 1);
        solution.addEngCritMultiplier(multiplyEng - 1);
        solution.addTacCritMultiplier(multiplyTac - 1);
        solution.addSciCritMultiplier(multiplySci - 1);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (obj instanceof RewardMultiplyCritRate reward) {
            if (multiplyEng != reward.multiplyEng)
                return false;
            if (multiplyTac != reward.multiplyTac)
                return false;
            if (multiplySci != reward.multiplySci)
                return false;
            return multiplyBaseCritRate == reward.multiplyBaseCritRate;
        }
        return false;
    }

    @Override
    public String toString() {
        return Strings.toFunctionString(this,
                String.format("multiplyEng: %.1f, multiplyTac: %.1f, multiplySci: %.1f, multiplyBaseCritRate: %.1f",
                        multiplyEng, multiplyTac, multiplySci, multiplyBaseCritRate));
    }

}
