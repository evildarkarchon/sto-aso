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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.kor.admiralty.beans;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.io.GameData;

/**
 * Specifies Admiral roster behavior at the public GameData attachment seam.
 */
class AdmiralGameDataTest {

	/**
	 * Verifies validation canonicalizes persisted names without leaking Roster or usage state into GameData Ships.
	 */
	@Test
	void validationCanonicalizesSavedShipNamesWithoutMutatingGameDataShips() {
		Ship canonicalShip = ship("Canonical Ship", Tier.Tier6);
		Ship alphaShuttle = ship("Alpha Shuttle", Tier.Tier1);
		Ship oneTimeOnly = ship("One-Time Only", Tier.Tier2);
		Ship usageOnly = ship("Usage Only", Tier.Tier3);
		GameData gameData = GameData.builder()
				.ships(List.of(canonicalShip, alphaShuttle, oneTimeOnly, usageOnly))
				.renamedShips(Map.of("Former Ship", canonicalShip.getName()))
				.build();
		Admiral admiral = new Admiral();
		admiral.setActive(new ArrayList<>(List.of("fOrMeR sHiP", "Unknown Active")));
		admiral.setMaintenance(new ArrayList<>(List.of("ALPHA SHUTTLE", "Unknown Maintenance")));
		admiral.setOneTime(new ArrayList<>(List.of("one-time only", "Unknown One-Time")));
		admiral.setUsage(new HashMap<>(Map.of(
                "FORMER SHIP", 3,
                "alpha shuttle", 2,
                "usage only", 1,
                "Unknown Usage", 7)));

		admiral.attach(gameData);
		admiral.validateShips();

		assertEquals(List.of("Canonical Ship"), admiral.getActive());
		assertEquals(List.of("Alpha Shuttle"), admiral.getMaintenance());
		assertEquals(List.of("One-Time Only"), admiral.getOneTime());
		assertEquals(Map.of("Canonical Ship", 3, "Alpha Shuttle", 2, "Usage Only", 1), admiral.getUsage());
		assertFalse(canonicalShip.isOwned());
		assertFalse(alphaShuttle.isOwned());
		assertFalse(oneTimeOnly.isOwned());
		assertFalse(usageOnly.isOwned());
	}

	/**
	 * Verifies each roster view returns builder-provided Ships in their natural sorted order.
	 */
	@Test
	void rosterViewsReturnShipsInSortedOrder() {
		Ship zuluActive = ship("Zulu Active", Tier.Tier6);
		Ship alphaActive = ship("Alpha Active", Tier.Tier1);
		Ship zuluMaintenance = ship("Zulu Maintenance", Tier.Tier6);
		Ship alphaMaintenance = ship("Alpha Maintenance", Tier.Tier1);
		GameData gameData = GameData.builder()
				.ships(List.of(zuluActive, alphaActive, zuluMaintenance, alphaMaintenance))
				.build();
		Admiral admiral = new Admiral();
		admiral.setActive(new ArrayList<>(List.of(zuluActive.getName(), alphaActive.getName())));
		admiral.setMaintenance(new ArrayList<>(List.of(zuluMaintenance.getName(), alphaMaintenance.getName())));
		admiral.setOneTime(new ArrayList<>(List.of(zuluActive.getName(), alphaActive.getName())));

		admiral.attach(gameData);

		assertEquals(List.of(alphaActive.getName(), zuluActive.getName()), shipNames(admiral.getActiveShips()));
		assertEquals(
				List.of(alphaMaintenance.getName(), zuluMaintenance.getName()),
				shipNames(admiral.getMaintenanceShips()));
		assertEquals(List.of(alphaActive.getName(), zuluActive.getName()), shipNames(admiral.getOneTimeShips()));
	}

	/**
	 * Verifies every public Admiral operation that resolves Ship names fails loudly before attachment.
	 */
	@Test
	void lookupDependentOperationsThrowBeforeAttach() {
		Admiral admiral = new Admiral();

		assertAll(
				() -> assertThrows(IllegalStateException.class, admiral::getActiveShips),
				() -> assertThrows(IllegalStateException.class, admiral::getMaintenanceShips),
				() -> assertThrows(IllegalStateException.class, admiral::getOneTimeShips),
				() -> assertThrows(IllegalStateException.class, admiral::getStarshipTraits),
				() -> assertThrows(IllegalStateException.class, admiral::validateShips));
	}

	/**
	 * Verifies an attached Admirals container automatically attaches a newly added Admiral.
	 */
	@Test
	void addingAdmiralToAttachedContainerAttachesIt() {
		Ship ship = ship("Attached Ship", Tier.Tier1);
		GameData gameData = GameData.builder().ships(List.of(ship)).build();
		Admirals admirals = new Admirals();
		Admiral addedAdmiral = new Admiral();
		addedAdmiral.addActive(ship.getName());

		admirals.attach(gameData);
		admirals.addAdmiral(addedAdmiral);

		assertEquals(List.of(ship.getName()), shipNames(addedAdmiral.getActiveShips()));
	}

	/**
	 * Verifies Admirals owns creation of default and newly added Admirals whose empty Rosters are valid immediately.
	 */
	@Test
	void admiralsCreatesLookupReadyEmptyRosters() {
		GameData gameData = GameData.builder().build();
		Admirals admirals = new Admirals(gameData);
		Admiral defaultAdmiral = admirals.getAdmirals().get(0);

		Admiral addedAdmiral = admirals.addAdmiral();

		assertAll(
				() -> assertTrue(defaultAdmiral.getActiveShips().isEmpty()),
				() -> assertTrue(defaultAdmiral.getMaintenanceShips().isEmpty()),
				() -> assertTrue(defaultAdmiral.getOneTimeShips().isEmpty()),
				() -> assertTrue(defaultAdmiral.getUsage().isEmpty()),
				() -> assertTrue(addedAdmiral.getActiveShips().isEmpty()),
				() -> assertTrue(addedAdmiral.getMaintenanceShips().isEmpty()),
				() -> assertTrue(addedAdmiral.getOneTimeShips().isEmpty()),
				() -> assertTrue(addedAdmiral.getUsage().isEmpty()));
	}

	/**
	 * Verifies attaching an Admirals container forwards GameData to every Admiral it already holds.
	 */
	@Test
	void attachingContainerAttachesEveryExistingAdmiral() {
		Ship ship = ship("Existing Admiral Ship", Tier.Tier1);
		GameData gameData = GameData.builder().ships(List.of(ship)).build();
		Admirals admirals = new Admirals();
		Admiral existingAdmiral = admirals.getAdmirals().get(0);
		existingAdmiral.addActive(ship.getName());

		admirals.attach(gameData);

		assertEquals(List.of(ship.getName()), shipNames(existingAdmiral.getActiveShips()));
	}

	/**
	 * Verifies replacing the contents of an attached container preserves its attachment invariant.
	 */
	@Test
	void replacingAdmiralsInAttachedContainerAttachesThem() {
		Ship ship = ship("Replacement Admiral Ship", Tier.Tier1);
		GameData gameData = GameData.builder().ships(List.of(ship)).build();
		Admirals admirals = new Admirals();
		admirals.attach(gameData);
		Admiral replacement = new Admiral();
		replacement.addActive(ship.getName());

		admirals.setAdmirals(new ArrayList<>(List.of(replacement)));

		assertEquals(List.of(ship.getName()), shipNames(replacement.getActiveShips()));
	}

	/**
	 * Verifies usage projection fails loudly when its owning Admirals container is unattached.
	 */
	@Test
	void usageProjectionThrowsBeforeContainerAttach() {
		Admirals admirals = new Admirals();

		assertThrows(IllegalStateException.class, admirals::getShipUsageRows);
	}

	/**
	 * Verifies usage projection resolves attached GameData names without rewriting its canonical Ships.
	 */
	@Test
	void usageProjectionUsesAttachedGameDataWithoutMutation() {
		Ship alphaShip = ship("Alpha Ship", Tier.Tier1);
		Ship zuluShip = ship("Zulu Ship", Tier.Tier6);
		GameData gameData = GameData.builder()
				.ships(List.of(alphaShip, zuluShip))
				.renamedShips(Map.of("Former Zulu", zuluShip.getName()))
				.build();
		Admiral first = new Admiral();
		first.addActive("ALPHA SHIP");
		first.setUsage(new HashMap<>(Map.of("former zulu", 2)));
		Admiral second = new Admiral();
		second.addMaintenance(alphaShip.getName());
		second.setUsage(new HashMap<>(Map.of("ZULU SHIP", 3)));
		Admirals admirals = new Admirals();
		admirals.setAdmirals(new ArrayList<>(List.of(first, second)));
		admirals.attach(gameData);

		List<ShipUsageRow> usageRows = admirals.getShipUsageRows(first, second);

		assertEquals(
				List.of(alphaShip.getName(), zuluShip.getName()),
				usageRows.stream()
						.map(row -> row.getShip().getName())
						.collect(Collectors.toList()));
		assertEquals(0, usageRows.get(0).getDeploymentCount());
		assertEquals(5, usageRows.get(1).getDeploymentCount());
	}

	/**
	 * Projects a public Ship collection to names for behavior-focused assertions.
	 *
	 * @param ships Ships returned by a roster view
	 * @return Ship names in iteration order
	 */
	private static List<String> shipNames(Collection<Ship> ships) {
		return ships.stream().map(Ship::getName).collect(Collectors.toList());
	}

	/**
	 * Creates a mutable Ship with representative data and a caller-selected natural sort tier.
	 *
	 * @param name canonical Ship name
	 * @param tier Ship tier used by natural ordering
	 * @return mutable Ship for builder-made GameData
	 */
	private static Ship ship(String name, Tier tier) {
		return new ShipImpl(
				ShipFaction.Federation,
				tier,
				Rarity.Common,
				Role.Eng,
				name,
				10,
				10,
				10,
				RuleType.All.rewardBonus(0),
				"");
	}
}
