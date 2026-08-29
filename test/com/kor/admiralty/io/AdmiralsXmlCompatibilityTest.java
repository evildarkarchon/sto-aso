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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.Admirals;
import com.kor.admiralty.enums.PlayerFaction;

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
	 * Loads an existing-format fixture and saves it with the same element names and namespace.
	 *
	 * @throws Exception if the fixture, JAXB round trip, or XML inspection fails
	 */
	@Test
	void existingAdmiralsXmlRoundTripsWithoutFormatChanges() throws Exception {
		copyFixtureToTempDirectory();
		AdmiralsStore store = new AdmiralsStore();
		Admirals admirals = store.loadOrCreate(tempDir);

		assertEquals(1, admirals.getAdmirals().size());
		Admiral admiral = admirals.getAdmirals().get(0);
		assertEquals("Existing Admiral", admiral.getName());
		assertEquals(PlayerFaction.Federation, admiral.getFaction());
		assertEquals(List.of("Class F Shuttle"), admiral.getActive());
		assertEquals(List.of("Danube Runabout"), admiral.getMaintenance());
		assertEquals(List.of("Type 10 Shuttle"), admiral.getOneTime());
		assertEquals(Integer.valueOf(3), admiral.getUsage().get("Class F Shuttle"));

		Path outputDirectory = Files.createDirectory(tempDir.resolve("saved"));
		store.save(outputDirectory, admirals);
		Path output = outputDirectory.resolve(FILENAME_ADMIRALS);

		Document document = parse(output);
		assertEquals("admirals", document.getDocumentElement().getTagName());
		assertEquals(XML_ELEMENT_NAMES, elementNames(document));
		assertElementsHaveNoNamespace(document);
	}

	/**
	 * Verifies attaching GameData does not add or alter any persisted JAXB content.
	 *
	 * @throws Exception if temporary directories or persisted XML cannot be created
	 */
	@Test
	void attachedContainerMarshalsExactlyLikeUnattachedContainer() throws Exception {
		Admirals admirals = new Admirals();
		Path unattachedDirectory = Files.createDirectory(tempDir.resolve("unattached"));
		Path attachedDirectory = Files.createDirectory(tempDir.resolve("attached"));
		AdmiralsStore store = new AdmiralsStore();
		store.save(unattachedDirectory, admirals);

		admirals.attach(GameData.builder().build());
		store.save(attachedDirectory, admirals);

		assertEquals(
				Files.readString(unattachedDirectory.resolve(FILENAME_ADMIRALS)),
				Files.readString(attachedDirectory.resolve(FILENAME_ADMIRALS)));
	}

	/**
	 * Copies the immutable classpath fixture before passing it to the file-based persistence seam.
	 *
	 * @throws Exception if the fixture is missing or cannot be copied
	 */
	private void copyFixtureToTempDirectory() throws Exception {
		Path input = tempDir.resolve(FILENAME_ADMIRALS);
		try (InputStream fixture = getClass().getResourceAsStream("/admirals/existing-admirals.xml")) {
			assertNotNull(fixture, "The existing Admirals XML fixture must be on the test classpath");
			Files.copy(fixture, input);
		}
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
