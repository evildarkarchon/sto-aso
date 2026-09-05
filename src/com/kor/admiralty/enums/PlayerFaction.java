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
package com.kor.admiralty.enums;

import static com.kor.admiralty.ui.resources.Strings.Shared.*;

/**
 * Enumeration of all player factions in STO
 *
 *
 */
public enum PlayerFaction {
    Federation, Klingon, RomulanFed, RomulanKDF, JemHadarFed, JemHadarKDF;

    private static String toString(PlayerFaction faction) {
        return switch (faction) {
            case Federation -> PlayerFederation;
            case Klingon -> PlayerKlingon;
            case RomulanFed -> PlayerRomulanFed;
            case RomulanKDF -> PlayerRomulanKDF;
            case JemHadarFed -> PlayerJemHadarFed;
            case JemHadarKDF -> PlayerJemHadarKDF;
            default -> PlayerUnknown;
        };
    }

    public static PlayerFaction fromString(String string) {
        if (string == null) {
            throw new IllegalArgumentException();
        }
        for (PlayerFaction faction : values()) {
            if (faction.toString().equalsIgnoreCase(string)) {
                return faction;
            }
        }
        throw new IllegalArgumentException();
    }

    @Override
    public String toString() {
        return toString(this);
    }

}
