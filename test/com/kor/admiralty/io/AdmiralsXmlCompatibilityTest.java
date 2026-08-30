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

import static com.kor.admiralty.Globals.FILENAME_ADMIRALS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterCardKind;
import com.kor.admiralty.beans.RosterView;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.PlayerFaction;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;

/**
 * Protects the on-disk Admirals XML contract while JAXB runs outside the JDK.
 */
class AdmiralsXmlCompatibilityTest {

	private static final Set<String> XML_ELEMENT_NAMES = Set.of(
			"admirals", "admiral", "name", "faction", "active", "maintenance",
			"onetime", "usage", "entry", "key", "value");

	@TempDir
	Path tempDir;

	/**
	 * Loads the historical Roster matrix into canonical state while preserving One-Time multiplicity.
	 *
	 * @throws Exception if the fixture cannot be copied or loaded through AdmiralsStore
	 */
	@Test
	void historicalRosterMatrixRestoresCanonicalState() throws Exception {
		Admirals admirals = loadFixture();

		assertEquals(1, admirals.getAdmirals().size());
		Admiral admiral = admirals.getAdmirals().get(0);
		assertEquals("Historical Admiral", admiral.getName());
		assertEquals(PlayerFaction.Federation, admiral.getFaction());
		assertEquals(
				List.of("Class F Shuttle", "Type 10 Shuttle"),
				cardNames(admiral.getRoster().getActiveCardsInRosterOrder()));
		assertEquals(
				List.of("Danube Runabout", "Conflict Cruiser"),
				cardNames(admiral.getRoster().getMaintenanceCardsInRosterOrder()));
		assertEquals(
				List.of("Type 10 Shuttle", "Type 10 Shuttle"),
				cardNames(admiral.getRoster().getOneTimeCardsInRosterOrder()));
		RosterView roster = admiral.getRoster();
		RosterCard reusableTypeTen = roster.getActiveCards().stream()
				.filter(card -> card.getShip().getName().equals("Type 10 Shuttle"))
				.findFirst()
				.orElseThrow();
		RosterCard firstOneTimeTypeTen = roster.getOneTimeCards().get(0);
		RosterCard secondOneTimeTypeTen = roster.getOneTimeCards().get(1);
		assertEquals(2, roster.getOneTimeQuantity(reusableTypeTen.getShip()));
		assertEquals(RosterCardKind.REUSABLE, reusableTypeTen.getKind());
		assertEquals(RosterCardKind.ONE_TIME, firstOneTimeTypeTen.getKind());
		assertSame(reusableTypeTen.getShip(), firstOneTimeTypeTen.getShip());
		assertSame(reusableTypeTen.getShip(), secondOneTimeTypeTen.getShip());
		assertNotEquals(reusableTypeTen.getId(), firstOneTimeTypeTen.getId());
		assertNotEquals(firstOneTimeTypeTen.getId(), secondOneTimeTypeTen.getId());
		assertEquals(
				Map.of("Danube Runabout", 7, "Class F Shuttle", 3),
				admiral.getUsageCounts());
		assertFalse(admiral.getPrioritizeActive());
	}

	/**
	 * Verifies the restored historical Roster supports Ship lookup without a later attachment or validation step.
	 *
	 * @throws Exception if the fixture cannot be copied or loaded through AdmiralsStore
	 */
	@Test
	void historicalRosterIsReadyForShipLookup() throws Exception {
		Admiral admiral = loadFixture().getAdmirals().get(0);

		assertEquals(
				List.of("Class F Shuttle", "Type 10 Shuttle"),
				cardNames(admiral.getRoster().getActiveCards()));
		assertEquals(
				List.of("Conflict Cruiser", "Danube Runabout"),
				cardNames(admiral.getRoster().getMaintenanceCards()));
		assertEquals(
				List.of("Type 10 Shuttle", "Type 10 Shuttle"),
				cardNames(admiral.getRoster().getOneTimeCards()));
	}

	/**
	 * Verifies the persisted reusable-priority preference still puts One-Time Ships first.
	 *
	 * @throws Exception if the fixture cannot be copied or loaded through AdmiralsStore
	 */
	@Test
	void historicalOneTimePriorityRemainsObservable() throws Exception {
		Admiral admiral = loadFixture().getAdmirals().get(0);

		List<RosterCard> deployable = admiral.getRoster().getDeployableCards(admiral.getPrioritizeActive());

		assertEquals(
				List.of(
						"Type 10 Shuttle",
						"Type 10 Shuttle",
						"Class F Shuttle",
						"Type 10 Shuttle"),
				cardNames(deployable));
	}

	/**
	 * Saves canonical historical state with the same element names, ordering, namespace, and map shape.
	 *
	 * @throws Exception if the fixture, JAXB round trip, or XML inspection fails
	 */
	@Test
	void historicalRosterSavesCanonicalStateWithoutSchemaChanges() throws Exception {
		AdmiralsStore store = new AdmiralsStore();
		Admirals admirals = loadFixture(store);

		Path outputDirectory = Files.createDirectory(tempDir.resolve("saved"));
		store.save(outputDirectory, admirals);
		Path output = outputDirectory.resolve(FILENAME_ADMIRALS);

		Document document = parse(output);
		Element root = document.getDocumentElement();
		assertEquals("admirals", root.getTagName());
		assertEquals(List.of("admiral"), directElementNames(root));

		Element admiral = directChildElements(root).get(0);
		assertEquals("false", admiral.getAttribute("prioritizeActive"));
		assertEquals(
				List.of(
						"name",
						"faction",
						"active", "active",
						"maintenance", "maintenance",
						"onetime", "onetime",
						"usage"),
				directElementNames(admiral));
		assertEquals(
				List.of("Class F Shuttle", "Type 10 Shuttle"),
				directChildElements(admiral).stream()
						.filter(element -> element.getTagName().equals("active"))
						.map(Element::getTextContent)
						.collect(Collectors.toList()));
		assertEquals(
				List.of("Danube Runabout", "Conflict Cruiser"),
				directChildElements(admiral).stream()
						.filter(element -> element.getTagName().equals("maintenance"))
						.map(Element::getTextContent)
						.collect(Collectors.toList()));
		assertEquals(
				List.of("Type 10 Shuttle", "Type 10 Shuttle"),
				directChildElements(admiral).stream()
						.filter(element -> element.getTagName().equals("onetime"))
						.map(Element::getTextContent)
						.collect(Collectors.toList()));

		Element usage = directChildElements(admiral).stream()
				.filter(element -> element.getTagName().equals("usage"))
				.findFirst()
				.orElseThrow();
		assertEquals(List.of("entry", "entry"), directElementNames(usage));
		for (Element entry : directChildElements(usage)) {
			assertEquals(List.of("key", "value"), directElementNames(entry));
		}
		Map<String, String> savedUsage = directChildElements(usage).stream()
				.collect(Collectors.toMap(
						entry -> directChildElements(entry).get(0).getTextContent(),
						entry -> directChildElements(entry).get(1).getTextContent()));
		assertEquals(Map.of("Danube Runabout", "7", "Class F Shuttle", "3"), savedUsage);

		assertEquals(XML_ELEMENT_NAMES, elementNames(document));
		assertElementsHaveNoNamespace(document);
	}

	/**
	 * Verifies concrete GameData dependencies do not add or alter persisted JAXB content.
	 *
	 * @throws Exception if temporary directories or persisted XML cannot be created
	 */
	@Test
	void gameDataDependencyIsNotMarshalled() throws Exception {
		Admirals emptyGameDataAdmirals = new Admirals(GameData.builder().build());
		Admirals populatedGameDataAdmirals = new Admirals(gameData());
		Path emptyGameDataDirectory = Files.createDirectory(tempDir.resolve("empty-gamedata"));
		Path populatedGameDataDirectory = Files.createDirectory(tempDir.resolve("populated-gamedata"));
		AdmiralsStore store = new AdmiralsStore();
		store.save(emptyGameDataDirectory, emptyGameDataAdmirals);
		store.save(populatedGameDataDirectory, populatedGameDataAdmirals);

		assertEquals(
				Files.readString(emptyGameDataDirectory.resolve(FILENAME_ADMIRALS)),
				Files.readString(populatedGameDataDirectory.resolve(FILENAME_ADMIRALS)));
	}

	/**
	 * Copies the immutable classpath fixture before passing it to the file-based persistence seam.
	 *
	 * @throws Exception if the fixture is missing or cannot be copied
	 */
	private void copyFixtureToTempDirectory() throws Exception {
		Path input = tempDir.resolve(FILENAME_ADMIRALS);
		try (InputStream fixture = getClass().getResourceAsStream("/admirals/historical-compatibility-matrix.xml")) {
			assertNotNull(fixture, "The existing Admirals XML fixture must be on the test classpath");
			Files.copy(fixture, input);
		}
	}

	/**
	 * Copies and loads the historical fixture through the public persistence seam.
	 *
	 * @return Admirals reconstructed from the historical fixture
	 * @throws Exception if AdmiralsStore cannot initialize or load the fixture
	 */
	private Admirals loadFixture() throws Exception {
		return loadFixture(new AdmiralsStore());
	}

	/**
	 * Copies and loads the historical fixture through a caller-owned store used for a later save.
	 *
	 * @param store persistence seam shared by the load and save operations
	 * @return Admirals reconstructed from the historical fixture
	 * @throws Exception if the fixture cannot be copied or loaded
	 */
	private Admirals loadFixture(AdmiralsStore store) throws Exception {
		copyFixtureToTempDirectory();
		return store.loadOrCreate(tempDir, gameData());
	}

	/**
	 * Builds concrete reference data for canonicalizing every Ship name in the historical fixture.
	 *
	 * @return GameData containing every canonical and renamed Ship needed by the fixture
	 */
	private static GameData gameData() {
		return GameData.builder()
				.ships(List.of(
						ship("Class F Shuttle"),
						ship("Danube Runabout"),
						ship("Conflict Cruiser"),
						ship("Type 10 Shuttle")))
				.renamedShips(Map.of("Former Runabout", "Danube Runabout"))
				.build();
	}

	/**
	 * Creates a concrete Ship suitable for the in-memory historical GameData fixture.
	 *
	 * @param name canonical Ship name
	 * @return representative Ship using stable attributes for natural ordering
	 */
	private static Ship ship(String name) {
		return new ShipImpl(
				ShipFaction.Federation,
				Tier.Tier1,
				Rarity.Common,
				Role.Eng,
				name,
				10,
				10,
				10,
				RuleType.All.rewardBonus(0),
				"");
	}

	/**
	 * Projects Roster cards to canonical Ship names without hiding collection ordering decisions.
	 *
	 * @param cards cards in the order exposed by Admiral
	 * @return canonical Ship names in the same order
	 */
	private static List<String> cardNames(Collection<RosterCard> cards) {
		return cards.stream().map(card -> card.getShip().getName()).collect(Collectors.toList());
	}

	/**
	 * Parses XML with namespace awareness so an accidental namespace change remains observable.
	 */
	private static Document parse(Path xml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(xml.toFile());
	}

	/**
	 * Returns every distinct element name emitted by JAXB.
	 */
	private static Set<String> elementNames(Document document) {
		Set<String> names = new HashSet<>();
		for (Element element : elements(document)) {
			names.add(element.getTagName());
		}
		return names;
	}

	/**
	 * Returns the direct child element names in document order.
	 *
	 * @param parent element whose immediate child names are required
	 * @return direct child tag names in document order
	 */
	private static List<String> directElementNames(Element parent) {
		return directChildElements(parent).stream()
				.map(Element::getTagName)
				.collect(Collectors.toList());
	}

	/**
	 * Returns only a parent's direct child elements, excluding indentation text nodes.
	 *
	 * @param parent element whose immediate children are required
	 * @return direct child elements in document order
	 */
	private static List<Element> directChildElements(Element parent) {
		NodeList nodes = parent.getChildNodes();
		List<Element> children = new ArrayList<>();
		for (int index = 0; index < nodes.getLength(); index++) {
			Node node = nodes.item(index);
			if (node instanceof Element) {
				children.add((Element)node);
			}
		}
		return children;
	}

	/**
	 * Verifies every persisted element remains in the historical empty namespace.
	 */
	private static void assertElementsHaveNoNamespace(Document document) {
		for (Element element : elements(document)) {
			assertNull(element.getNamespaceURI(), element.getTagName() + " must not gain an XML namespace");
		}
	}

	/**
	 * Returns the document's elements in document order.
	 */
	private static List<Element> elements(Document document) {
		NodeList nodes = document.getElementsByTagName("*");
		List<Element> elements = new ArrayList<>(nodes.getLength());
		for (int index = 0; index < nodes.getLength(); index++) {
			elements.add((Element)nodes.item(index));
		}
		return elements;
	}
}
