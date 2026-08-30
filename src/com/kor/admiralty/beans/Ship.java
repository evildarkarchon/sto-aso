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

import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;

public interface Ship extends Comparable<Ship> {

    ShipFaction getFaction();

    void setFaction(ShipFaction faction);

    Tier getTier();

    void setTier(Tier tier);

    Rarity getRarity();

    void setRarity(Rarity rarity);

    Role getRole();

    void setRole(Role role);

    String getName();

    void setName(String name);

    String getIconName();

    String getDisplayName();

    int getEng();

    void setEng(int eng);

    int getTac();

    void setTac(int tac);

    int getSci();

    void setSci(int sci);

    SpecialAbility getSpecialAbility();

    void setSpecialAbility(SpecialAbility rule);

    String getTrait();

    void setTrait(String trait);

    boolean hasTrait();

}
