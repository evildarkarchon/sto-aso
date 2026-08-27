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
package com.kor.admiralty.io;

import java.io.IOException;
import java.io.Reader;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.beans.SpecialAbility;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;

public class ShipDatabaseParser {

	/**
	 * Parses Ships and resolves their Starship Traits from caller-supplied reference data.
	 *
	 * @param reader Ship CSV source, closed when parsing completes
	 * @param ships destination map keyed by case-folded Ship name
	 * @param traits Starship Trait names mapped to resolved descriptions
	 * @throws IllegalArgumentException if a Ship record contains invalid reference data
	 */
	public static void loadShipDatabase(
			Reader reader,
			SortedMap<String, Ship> ships,
			Map<String, String> traits) {
		try {
			for (CSVRecord record : CSVFormat.EXCEL.withHeader().parse(reader)) {
				Ship ship = loadShipRecord(record, traits);
				ships.put(ship.getName().toLowerCase(Locale.ROOT), ship);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Parses one Ship record, resolving its optional Starship Trait without reaching a data store.
	 *
	 * @param record CSV record to parse
	 * @param traits Starship Trait names mapped to resolved descriptions
	 * @return the parsed mutable Ship
	 * @throws IllegalArgumentException if the record contains invalid reference data
	 */
	private static Ship loadShipRecord(CSVRecord record, Map<String, String> traits) {
		ShipFaction faction = ShipFaction.valueOf(record.get("Faction").trim());
		int t = Integer.parseInt(record.get("Tier").trim());
		Tier tier = Tier.None;
		if (t == 0) tier = Tier.SmallCraft;
		else if (t == 1) tier = Tier.Tier1;
		else if (t == 2) tier = Tier.Tier2;
		else if (t == 3) tier = Tier.Tier3;
		else if (t == 4) tier = Tier.Tier4;
		else if (t == 5) tier = Tier.Tier5;
		else if (t == 6) tier = Tier.Tier6;
		Rarity rarity = Rarity.fromString(record.get("Rarity").trim());
		Role category = Role.valueOf(record.get("Type").trim());
		// Just in case LibreOffice Calc replaced dashes ''' with '’'
		String name = record.get("Name").trim().replace('’', '\'');
		int eng = Integer.parseInt(record.get("Eng").trim());
		int tac = Integer.parseInt(record.get("Tac").trim());
		int sci = Integer.parseInt(record.get("Sci").trim());
		SpecialAbility ability = SpecialAbilityParser2.parse(record.get("Bonus").trim());
		String trait = resolveStarshipTrait(record.get("Trait").trim(), traits);
		return new ShipImpl(faction, tier, rarity, category, name, eng, tac, sci, ability, trait);
	}

	/**
	 * Resolves a Starship Trait name to its description while preserving unknown or empty names.
	 *
	 * @param trait Starship Trait name, or null
	 * @param traits case-folded trait names mapped to resolved descriptions
	 * @return the resolved description when present, otherwise the supplied value
	 */
	static String resolveStarshipTrait(String trait, Map<String, String> traits) {
		if (trait == null || trait.isEmpty()) {
			return trait;
		}
		String value = traits.get(trait.toLowerCase(Locale.ROOT));
		return value == null || value.isEmpty() ? trait : value;
	}
	
}
