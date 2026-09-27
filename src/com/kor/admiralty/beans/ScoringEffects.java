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

/**
 * Accepts Special Ability effects during a Solver calculation. Solver owns the
 * mutable implementation and does not expose it with a published Solution.
 */
public interface ScoringEffects {

    /** Adds Ship or reward statistics to the current candidate. */
    void addStats(int eng, int tac, int sci);

    /** Replaces all Event-ignore flags, including flags set to {@code false}. */
    void setIgnoredEventStats(boolean eng, boolean tac, boolean sci);

    /** Adds critical multiplier deltas to the initial one-times values. */
    void addCriticalMultiplierDeltas(double event, double eng, double tac, double sci);

    /** Returns the number of selected Ships, excluding empty slots. */
    int selectedShipCount();
}
