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
package com.kor.admiralty.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.PlayerFaction;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;

/**
 * Specifies Admiral-to-file behavior through the public AdmiralsStore seam.
 */
class AdmiralsStoreTest {

	@TempDir
	Path tempDir;

	/**
	 * Verifies complete canonical Admiral state survives a directory-backed XML round trip ready for lookup.
	 *
	 * @throws AdmiralsStoreException if the store cannot initialize or persist Admirals XML
	 */
	@Test
	void admiralsRoundTripThroughXml() throws AdmiralsStoreException {
		Admiral expected = new Admiral(GameData.builder().build());
		expected.setName("Round Trip Admiral");
		expected.setFaction(PlayerFaction.RomulanKDF);
		expected.setActive(new ArrayList<>(List.of("Active Ship")));
		expected.setMaintenance(new ArrayList<>(List.of("Maintenance Ship")));
		expected.setOneTime(new ArrayList<>(List.of("One-Time Ship")));
		expected.setUsage(new HashMap<>(Map.of("Active Ship", 7)));
		Admirals original = new Admirals(GameData.builder().build());
		original.setAdmirals(new ArrayList<>(List.of(expected)));
		GameData gameData = GameData.builder()
				.ships(List.of(ship("Active Ship"), ship("Maintenance Ship"), ship("One-Time Ship")))
				.build();
		AdmiralsStore store = new AdmiralsStore();

		store.save(tempDir, original);
		Admirals loaded = store.loadOrCreate(tempDir, gameData);

		assertEquals(1, loaded.getAdmirals().size());
		Admiral actual = loaded.getAdmirals().get(0);
		assertEquals(expected.getName(), actual.getName());
		assertEquals(expected.getFaction(), actual.getFaction());
		assertEquals(expected.getActive(), actual.getActive());
		assertEquals(expected.getMaintenance(), actual.getMaintenance());
		assertEquals(expected.getOneTime(), actual.getOneTime());
		assertEquals(expected.getUsage(), actual.getUsage());
		assertEquals(expected.getPrioritizeActive(), actual.getPrioritizeActive());
		assertEquals(List.of("Active Ship"), actual.getActiveShips().stream()
				.map(Ship::getName)
				.collect(Collectors.toList()));
	}

	/**
	 * Verifies loading supplies GameData before returning runtime Admirals and canonicalizes persisted Ship names.
	 *
	 * @throws AdmiralsStoreException if the store cannot initialize, persist, or restore Admirals XML
	 */
	@Test
	void loadRestoresAdmiralsReadyForShipLookup() throws AdmiralsStoreException {
		Ship canonicalShip = ship("Canonical Ship");
		GameData gameData = GameData.builder().ships(List.of(canonicalShip)).build();
		Admiral persistedAdmiral = new Admiral(GameData.builder().build());
		persistedAdmiral.setActive(new ArrayList<>(List.of("cAnOnIcAl sHiP")));
		AdmiralsStore store = saveAdmiralFixture(persistedAdmiral);

		Admirals loaded = store.loadOrCreate(tempDir, gameData);

		Admiral restoredAdmiral = loaded.getAdmirals().get(0);
		assertEquals(List.of(canonicalShip.getName()), restoredAdmiral.getActive());
		assertEquals(List.of(canonicalShip), new ArrayList<>(restoredAdmiral.getActiveShips()));
	}

	/**
	 * Verifies restoration canonicalizes saved Roster names, drops unknown Ships, collapses reusable
	 * duplicates, and conservatively keeps conflicts in Maintenance.
	 *
	 * @throws AdmiralsStoreException if the store cannot initialize, persist, or restore Admirals XML
	 */
	@Test
	void loadCanonicalizesRosterAndRepairsReusableConflicts() throws AdmiralsStoreException {
		Ship canonicalShip = ship("Canonical Ship");
		Ship conflictShip = ship("Conflict Ship");
		GameData gameData = GameData.builder()
				.ships(List.of(canonicalShip, conflictShip))
				.renamedShips(Map.of("Former Canonical Ship", canonicalShip.getName()))
				.build();
		Admiral persistedAdmiral = new Admiral(GameData.builder().build());
		persistedAdmiral.setActive(new ArrayList<>(List.of(
				"cAnOnIcAl sHiP",
				"Former Canonical Ship",
				"Unknown Ship",
				"Conflict Ship")));
		persistedAdmiral.setMaintenance(new ArrayList<>(List.of(
				"CONFLICT SHIP",
				"conflict ship")));
		persistedAdmiral.setOneTime(new ArrayList<>(List.of(
				"canonical ship",
				"FORMER CANONICAL SHIP")));
		AdmiralsStore store = saveAdmiralFixture(persistedAdmiral);

		Admiral restored = store.loadOrCreate(tempDir, gameData).getAdmirals().get(0);

		assertEquals(List.of(canonicalShip.getName()), restored.getActive());
		assertEquals(List.of(conflictShip.getName()), restored.getMaintenance());
		assertEquals(
				List.of(canonicalShip.getName(), canonicalShip.getName()),
				restored.getOneTime());
	}

	/**
	 * Verifies Renamed Ship and case-variant usage names sum under the canonical Ship name.
	 *
	 * @throws AdmiralsStoreException if the store cannot initialize, persist, or restore Admirals XML
	 */
	@Test
	void loadSumsRenamedAndCaseVariantUsageUnderCanonicalShipNames() throws AdmiralsStoreException {
		Ship canonicalShip = ship("Canonical Ship");
		GameData gameData = GameData.builder()
				.ships(List.of(canonicalShip))
				.renamedShips(Map.of("Former Ship", canonicalShip.getName()))
				.build();
		Admiral persistedAdmiral = new Admiral(GameData.builder().build());
		persistedAdmiral.setUsage(new HashMap<>(Map.of(
				"CANONICAL SHIP", 3,
				"Former Ship", 4,
				"Unknown Ship", 9)));
		AdmiralsStore store = saveAdmiralFixture(persistedAdmiral);

		Admiral restored = store.loadOrCreate(tempDir, gameData).getAdmirals().get(0);

		assertEquals(Map.of(canonicalShip.getName(), 7), restored.getUsage());
		assertFalse(canonicalShip.isOwned());
	}

	/**
	 * Verifies corrupt negative usage fails loading even when its Ship name would otherwise be dropped.
	 *
	 * @throws AdmiralsStoreException if fixture setup cannot initialize or persist Admirals XML
	 */
	@Test
	void loadRejectsNegativeUsageBeforeDroppingUnknownShips() throws AdmiralsStoreException {
		Admiral persistedAdmiral = new Admiral(GameData.builder().build());
		persistedAdmiral.setUsage(new HashMap<>(Map.of("Unknown Ship", -1)));
		AdmiralsStore store = saveAdmiralFixture(persistedAdmiral);

		assertThrows(
				AdmiralsStoreException.class,
				() -> store.loadOrCreate(tempDir, GameData.builder().build()));
	}

	/**
	 * Verifies summing historical names cannot wrap a canonical Ship's usage count.
	 *
	 * @throws AdmiralsStoreException if fixture setup cannot initialize or persist Admirals XML
	 */
	@Test
	void loadRejectsCanonicalUsageOverflow() throws AdmiralsStoreException {
		Ship canonicalShip = ship("Canonical Ship");
		GameData gameData = GameData.builder()
				.ships(List.of(canonicalShip))
				.renamedShips(Map.of("Former Ship", canonicalShip.getName()))
				.build();
		Admiral persistedAdmiral = new Admiral(GameData.builder().build());
		persistedAdmiral.setActive(new ArrayList<>(List.of(canonicalShip.getName())));
		persistedAdmiral.setUsage(new HashMap<>(Map.of(
				canonicalShip.getName(), Integer.MAX_VALUE,
				"Former Ship", 1)));
		AdmiralsStore store = saveAdmiralFixture(persistedAdmiral);

		AdmiralsStoreException failure = assertThrows(
				AdmiralsStoreException.class,
				() -> store.loadOrCreate(tempDir, gameData));

		assertInstanceOf(ArithmeticException.class, failure.getCause());
		assertFalse(canonicalShip.isOwned());
	}

	/**
	 * Verifies first use persists and returns the standard one-Admiral default container.
	 *
	 * @throws AdmiralsStoreException if the store cannot initialize or persist default Admirals XML
	 */
	@Test
	void loadOrCreateWritesDefaultAdmiralsOnFirstRun() throws AdmiralsStoreException {
		AdmiralsStore store = new AdmiralsStore();

		Admirals loaded = store.loadOrCreate(tempDir, GameData.builder().build());

		assertTrue(Files.isRegularFile(tempDir.resolve("admirals.xml")));
		assertEquals(1, loaded.getAdmirals().size());
		Admiral defaultAdmiral = loaded.getAdmirals().get(0);
		assertEquals("New Admiral", defaultAdmiral.getName());
		assertTrue(defaultAdmiral.getActiveShips().isEmpty());
	}

	/**
	 * Verifies malformed persisted XML fails fast through the store's checked load contract.
	 *
	 * @throws IOException if the corrupt fixture cannot be written or reread
	 * @throws AdmiralsStoreException if the store cannot initialize before exercising its load contract
	 */
	@Test
	void corruptAdmiralsXmlThrowsCheckedException() throws IOException, AdmiralsStoreException {
		Path corruptFile = tempDir.resolve("admirals.xml");
		Files.writeString(corruptFile, "<admirals><admiral>");
		AdmiralsStore store = new AdmiralsStore();

		AdmiralsStoreException failure = assertThrows(
				AdmiralsStoreException.class,
				() -> store.loadOrCreate(tempDir, GameData.builder().build()));
		assertInstanceOf(JAXBException.class, failure.getCause());
		assertEquals("<admirals><admiral>", Files.readString(corruptFile));
	}

	/**
	 * Verifies exported display names import through GameData as canonical persisted Ship names.
	 *
	 * @throws IOException if the exported text fixture cannot be read or rewritten
	 * @throws AdmiralsStoreException if the store cannot initialize
	 */
	@Test
	void exportedShipNamesImportCanonicallyWithDifferentCasing() throws IOException, AdmiralsStoreException {
		Ship alpha = ship("Canonical Alpha");
		Ship beta = ship("Canonical Beta");
		GameData gameData = GameData.builder().ships(List.of(alpha, beta)).build();
		Path shipList = tempDir.resolve("ships.txt");
		AdmiralsStore store = new AdmiralsStore();

		assertTrue(store.exportShipNames(shipList.toFile(), List.of(alpha, beta)));
		assertEquals(List.of(alpha.getDisplayName(), beta.getDisplayName()), Files.readAllLines(shipList));
		Files.write(shipList, List.of("cAnOnIcAl aLpHa", beta.getDisplayName()));
		Admiral imported = new Admiral(gameData);

		assertEquals(2, store.importShipNames(shipList.toFile(), gameData, imported));
		assertEquals(List.of(alpha.getName(), beta.getName()), imported.getActive());
	}

	/**
	 * Verifies a broken JAXB provider prevents store construction instead of leaving unusable state behind.
	 */
	@Test
	@ResourceLock(Resources.SYSTEM_PROPERTIES)
	void constructingStorePropagatesJaxbInitializationFailure() {
		String propertyName = JAXBContext.JAXB_CONTEXT_FACTORY;
		String previousFactory = System.getProperty(propertyName);
		try {
			System.setProperty(propertyName, "com.kor.admiralty.test.MissingJaxbContextFactory");

			AdmiralsStoreException failure = assertThrows(AdmiralsStoreException.class, AdmiralsStore::new);
			assertInstanceOf(JAXBException.class, failure.getCause());
		} finally {
			if (previousFactory == null) {
				System.clearProperty(propertyName);
			} else {
				System.setProperty(propertyName, previousFactory);
			}
		}
	}

	/**
	 * Persists one prepared Admiral through the public store seam for a subsequent load assertion.
	 *
	 * @param admiral runtime values to encode as an Admirals XML fixture
	 * @return the initialized store that wrote the fixture
	 * @throws AdmiralsStoreException if the fixture cannot be initialized or written
	 */
	private AdmiralsStore saveAdmiralFixture(Admiral admiral) throws AdmiralsStoreException {
		Admirals persisted = new Admirals(GameData.builder().build());
		persisted.setAdmirals(new ArrayList<>(List.of(admiral)));
		AdmiralsStore store = new AdmiralsStore();
		store.save(tempDir, persisted);
		return store;
	}

	/**
	 * Creates a mutable Ship for an in-memory canonical-name lookup seam.
	 *
	 * @param name canonical Ship name
	 * @return representative Ship with the requested name
	 */
	private static Ship ship(String name) {
		return new ShipImpl(
				ShipFaction.Federation,
				Tier.Tier6,
				Rarity.Epic,
				Role.Eng,
				name,
				50,
				50,
				50,
				RuleType.All.rewardBonus(0),
				"");
	}
}
