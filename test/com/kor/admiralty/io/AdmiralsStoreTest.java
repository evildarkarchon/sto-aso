/**
 * Copyright (C) 2026 Dave Kor
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
 */
package com.kor.admiralty.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	 * Verifies the complete persisted Admiral state survives a directory-backed XML round trip.
	 *
	 * @throws JAXBException if the store cannot initialize or persist Admirals XML
	 */
	@Test
	void admiralsRoundTripThroughXml() throws JAXBException {
		Admiral expected = new Admiral();
		expected.setName("Round Trip Admiral");
		expected.setFaction(PlayerFaction.RomulanKDF);
		expected.setActive(new ArrayList<String>(List.of("Active Ship")));
		expected.setMaintenance(new ArrayList<String>(List.of("Maintenance Ship")));
		expected.setOneTime(new ArrayList<String>(List.of("One-Time Ship")));
		expected.setUsage(new HashMap<String, Integer>(Map.of("Active Ship", 7)));
		Admirals original = new Admirals();
		original.setAdmirals(new ArrayList<Admiral>(List.of(expected)));
		AdmiralsStore store = new AdmiralsStore();

		store.save(tempDir, original);
		Admirals loaded = store.loadOrCreate(tempDir);

		assertEquals(1, loaded.getAdmirals().size());
		Admiral actual = loaded.getAdmirals().get(0);
		assertEquals(expected.getName(), actual.getName());
		assertEquals(expected.getFaction(), actual.getFaction());
		assertEquals(expected.getActive(), actual.getActive());
		assertEquals(expected.getMaintenance(), actual.getMaintenance());
		assertEquals(expected.getOneTime(), actual.getOneTime());
		assertEquals(expected.getUsage(), actual.getUsage());
		assertThrows(IllegalStateException.class, actual::getActiveShips);
	}

	/**
	 * Verifies first use persists and returns the standard one-Admiral default container.
	 *
	 * @throws JAXBException if the store cannot initialize or persist default Admirals XML
	 */
	@Test
	void loadOrCreateWritesDefaultAdmiralsOnFirstRun() throws JAXBException {
		AdmiralsStore store = new AdmiralsStore();

		Admirals loaded = store.loadOrCreate(tempDir);

		assertTrue(Files.isRegularFile(tempDir.resolve("admirals.xml")));
		assertEquals(1, loaded.getAdmirals().size());
		assertEquals("New Admiral", loaded.getAdmirals().get(0).getName());
	}

	/**
	 * Verifies malformed persisted XML fails fast through the store's checked load contract.
	 *
	 * @throws IOException if the corrupt fixture cannot be written or reread
	 * @throws JAXBException if the store cannot initialize before exercising its load contract
	 */
	@Test
	void corruptAdmiralsXmlThrowsCheckedException() throws IOException, JAXBException {
		Path corruptFile = tempDir.resolve("admirals.xml");
		Files.writeString(corruptFile, "<admirals><admiral>");
		AdmiralsStore store = new AdmiralsStore();

		assertThrows(JAXBException.class, () -> store.loadOrCreate(tempDir));
		assertEquals("<admirals><admiral>", Files.readString(corruptFile));
	}

	/**
	 * Verifies exported display names import through GameData as canonical persisted Ship names.
	 *
	 * @throws IOException if the exported text fixture cannot be read or rewritten
	 * @throws JAXBException if the store cannot initialize
	 */
	@Test
	void exportedShipNamesImportCanonicallyWithDifferentCasing() throws IOException, JAXBException {
		Ship alpha = ship("Canonical Alpha");
		Ship beta = ship("Canonical Beta");
		GameData gameData = GameData.builder().ships(List.of(alpha, beta)).build();
		Path shipList = tempDir.resolve("ships.txt");
		AdmiralsStore store = new AdmiralsStore();

		assertTrue(store.exportShipNames(shipList.toFile(), List.of(alpha, beta)));
		assertEquals(List.of(alpha.getDisplayName(), beta.getDisplayName()), Files.readAllLines(shipList));
		Files.write(shipList, List.of("cAnOnIcAl aLpHa", beta.getDisplayName()));
		Admiral imported = new Admiral();

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

			assertThrows(JAXBException.class, AdmiralsStore::new);
		} finally {
			if (previousFactory == null) {
				System.clearProperty(propertyName);
			} else {
				System.setProperty(propertyName, previousFactory);
			}
		}
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
