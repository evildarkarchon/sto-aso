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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterChange;
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
     * Creates one historical JAXB map entry for a usage fixture.
     */
    private static String usageEntry(String shipName, int count) {
        return "<entry><key>" + shipName + "</key><value>" + count + "</value></entry>";
    }

    /**
     * Projects canonical Ship names from immutable Roster cards.
     */
    private static List<String> cardNames(List<RosterCard> cards) {
        return cards.stream().map(card -> card.getShip().getName()).collect(Collectors.toList());
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

    /**
     * Verifies complete canonical Admiral state survives a directory-backed XML
     * round trip ready for lookup.
     *
     * @throws AdmiralsStoreException if the store cannot initialize or persist
     *                                Admirals XML
     */
    @Test
    void admiralsRoundTripThroughXml() throws AdmiralsStoreException {
        Ship activeShip = ship("Active Ship");
        Ship maintenanceShip = ship("Maintenance Ship");
        Ship oneTimeShip = ship("One-Time Ship");
        GameData gameData = GameData.builder()
                .ships(List.of(activeShip, maintenanceShip, oneTimeShip))
                .build();
        Admiral expected = Admiral.restore(
                gameData,
                "Round Trip Admiral",
                PlayerFaction.RomulanKDF,
                List.of(activeShip),
                List.of(maintenanceShip),
                List.of(oneTimeShip),
                Map.of(activeShip, 7),
                true);
        Admirals original = Admirals.restore(gameData, List.of(expected));
        AdmiralsStore store = new AdmiralsStore();

        store.save(tempDir, original);
        Admirals loaded = store.loadOrCreate(tempDir, gameData);

        assertEquals(1, loaded.getAdmirals().size());
        Admiral actual = loaded.getAdmirals().getFirst();
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getFaction(), actual.getFaction());
        assertEquals(
                cardNames(expected.getRoster().getActiveCardsInRosterOrder()),
                cardNames(actual.getRoster().getActiveCardsInRosterOrder()));
        assertEquals(
                cardNames(expected.getRoster().getMaintenanceCardsInRosterOrder()),
                cardNames(actual.getRoster().getMaintenanceCardsInRosterOrder()));
        assertEquals(
                cardNames(expected.getRoster().getOneTimeCardsInRosterOrder()),
                cardNames(actual.getRoster().getOneTimeCardsInRosterOrder()));
        assertEquals(expected.getUsageCounts(), actual.getUsageCounts());
        assertEquals(expected.getPrioritizeActive(), actual.getPrioritizeActive());
        assertEquals(List.of("Active Ship"), cardNames(actual.getRoster().getActiveCards()));
    }

    /**
     * Verifies loading supplies GameData before returning runtime Admirals and
     * canonicalizes persisted Ship names.
     *
     * @throws AdmiralsStoreException if the store cannot initialize or restore
     *                                Admirals XML
     * @throws IOException            if the historical XML fixture cannot be
     *                                written
     */
    @Test
    void loadRestoresAdmiralsReadyForShipLookup() throws AdmiralsStoreException, IOException {
        Ship canonicalShip = ship("Canonical Ship");
        GameData gameData = GameData.builder().ships(List.of(canonicalShip)).build();
        AdmiralsStore store = writeRosterFixture("<active>cAnOnIcAl sHiP</active>");

        Admirals loaded = store.loadOrCreate(tempDir, gameData);

        Admiral restoredAdmiral = loaded.getAdmirals().getFirst();
        assertEquals(List.of(canonicalShip.getName()), cardNames(restoredAdmiral.getRoster().getActiveCards()));
        assertSame(canonicalShip, restoredAdmiral.getRoster().getActiveCards().getFirst().getShip());
    }

    /**
     * Verifies restoration canonicalizes saved Roster names, drops unknown Ships,
     * collapses reusable
     * duplicates, and conservatively keeps conflicts in Maintenance.
     *
     * @throws AdmiralsStoreException if the store cannot initialize or restore
     *                                Admirals XML
     * @throws IOException            if the historical XML fixture cannot be
     *                                written
     */
    @Test
    void loadCanonicalizesRosterAndRepairsReusableConflicts() throws AdmiralsStoreException, IOException {
        Ship canonicalShip = ship("Canonical Ship");
        Ship conflictShip = ship("Conflict Ship");
        GameData gameData = GameData.builder()
                .ships(List.of(canonicalShip, conflictShip))
                .renamedShips(Map.of("Former Canonical Ship", canonicalShip.getName()))
                .build();
        AdmiralsStore store = writeRosterFixture(
                "<active>cAnOnIcAl sHiP</active>"
                        + "<active>Former Canonical Ship</active>"
                        + "<active>Unknown Ship</active>"
                        + "<active>Conflict Ship</active>"
                        + "<maintenance>CONFLICT SHIP</maintenance>"
                        + "<maintenance>conflict ship</maintenance>"
                        + "<onetime>canonical ship</onetime>"
                        + "<onetime>FORMER CANONICAL SHIP</onetime>");

        Admiral restored = store.loadOrCreate(tempDir, gameData).getAdmirals().getFirst();

        assertEquals(List.of(canonicalShip.getName()), cardNames(restored.getRoster().getActiveCards()));
        assertEquals(List.of(conflictShip.getName()), cardNames(restored.getRoster().getMaintenanceCards()));
        assertEquals(
                List.of(canonicalShip.getName(), canonicalShip.getName()),
                cardNames(restored.getRoster().getOneTimeCards()));
    }

    /**
     * Verifies Renamed Ship and case-variant usage names sum under the canonical
     * Ship name.
     *
     * @throws IOException            if the historical XML fixture cannot be
     *                                written
     * @throws AdmiralsStoreException if the store cannot initialize, persist, or
     *                                restore Admirals XML
     */
    @Test
    void loadSumsRenamedAndCaseVariantUsageUnderCanonicalShipNames() throws AdmiralsStoreException, IOException {
        Ship canonicalShip = ship("Canonical Ship");
        GameData gameData = GameData.builder()
                .ships(List.of(canonicalShip))
                .renamedShips(Map.of("Former Ship", canonicalShip.getName()))
                .build();
        AdmiralsStore store = writeAdmiralFixture(
                "<usage>"
                        + usageEntry("CANONICAL SHIP", 3)
                        + usageEntry("Former Ship", 4)
                        + usageEntry("Unknown Ship", 9)
                        + "</usage>");

        Admiral restored = store.loadOrCreate(tempDir, gameData).getAdmirals().getFirst();

        assertEquals(Map.of(canonicalShip.getName(), 7), restored.getUsageCounts());
    }

    /**
     * Verifies corrupt negative usage fails loading even when its Ship name would
     * otherwise be dropped.
     *
     * @throws IOException            if the corrupt historical XML fixture cannot
     *                                be written
     * @throws AdmiralsStoreException if fixture setup cannot initialize or persist
     *                                Admirals XML
     */
    @Test
    void loadRejectsNegativeUsageBeforeDroppingUnknownShips() throws AdmiralsStoreException, IOException {
        AdmiralsStore store = writeAdmiralFixture(
                "<usage>" + usageEntry("Unknown Ship", -1) + "</usage>");

        assertThrows(
                AdmiralsStoreException.class,
                () -> store.loadOrCreate(tempDir, GameData.builder().build()));
    }

    /**
     * Verifies summing historical names cannot wrap a canonical Ship's usage count.
     *
     * @throws IOException            if the overflowing historical XML fixture
     *                                cannot be written
     * @throws AdmiralsStoreException if fixture setup cannot initialize or persist
     *                                Admirals XML
     */
    @Test
    void loadRejectsCanonicalUsageOverflow() throws AdmiralsStoreException, IOException {
        Ship canonicalShip = ship("Canonical Ship");
        GameData gameData = GameData.builder()
                .ships(List.of(canonicalShip))
                .renamedShips(Map.of("Former Ship", canonicalShip.getName()))
                .build();
        AdmiralsStore store = writeAdmiralFixture(
                "<active>" + canonicalShip.getName() + "</active>"
                        + "<usage>"
                        + usageEntry(canonicalShip.getName(), Integer.MAX_VALUE)
                        + usageEntry("Former Ship", 1)
                        + "</usage>");

        AdmiralsStoreException failure = assertThrows(
                AdmiralsStoreException.class,
                () -> store.loadOrCreate(tempDir, gameData));

        assertInstanceOf(ArithmeticException.class, failure.getCause());
    }

    /**
     * Verifies first use persists and returns the standard one-Admiral default
     * container.
     *
     * @throws AdmiralsStoreException if the store cannot initialize or persist
     *                                default Admirals XML
     */
    @Test
    void loadOrCreateWritesDefaultAdmiralsOnFirstRun() throws AdmiralsStoreException {
        AdmiralsStore store = new AdmiralsStore();

        Admirals loaded = store.loadOrCreate(tempDir, GameData.builder().build());

        assertTrue(Files.isRegularFile(tempDir.resolve("admirals.xml")));
        assertEquals(1, loaded.getAdmirals().size());
        Admiral defaultAdmiral = loaded.getAdmirals().getFirst();
        assertEquals("New Admiral", defaultAdmiral.getName());
        assertTrue(defaultAdmiral.getRoster().getActiveCards().isEmpty());
    }

    /**
     * Verifies malformed persisted XML fails fast through the store's checked load
     * contract.
     *
     * @throws IOException            if the corrupt fixture cannot be written or
     *                                reread
     * @throws AdmiralsStoreException if the store cannot initialize before
     *                                exercising its load contract
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
     * Verifies exported display names import through GameData as canonical
     * persisted Ship names.
     *
     * @throws IOException            if the exported text fixture cannot be read or
     *                                rewritten
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
        List<RosterChange> changes = new ArrayList<RosterChange>();
        imported.addRosterChangeListener(changes::add);

        assertEquals(2, store.importShipNames(shipList.toFile(), gameData, imported));
        assertEquals(List.of(alpha.getName(), beta.getName()), cardNames(imported.getRoster().getActiveCards()));
        assertEquals(1, changes.size());
        assertEquals(1L, imported.getRoster().getRevision());
    }

    /**
     * Verifies a broken JAXB provider prevents store construction instead of
     * leaving unusable state behind.
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
     * Writes raw historical Roster elements without routing invalid pre-canonical
     * values through runtime Admiral.
     *
     * @param rosterElements repeated historical Active, Maintenance, and One-Time
     *                       elements
     * @return initialized store ready to restore the written fixture
     * @throws IOException            if the fixture cannot be written
     * @throws AdmiralsStoreException if the store cannot initialize
     */
    private AdmiralsStore writeRosterFixture(String rosterElements) throws IOException, AdmiralsStoreException {
        return writeAdmiralFixture(rosterElements + "<usage/>");
    }

    /**
     * Writes arbitrary historical Admiral child elements directly through the JAXB
     * wire format.
     *
     * @param admiralElements ordered child elements after name and faction
     * @return initialized store ready to restore the written fixture
     * @throws IOException            if the fixture cannot be written
     * @throws AdmiralsStoreException if the store cannot initialize
     */
    private AdmiralsStore writeAdmiralFixture(String admiralElements) throws IOException, AdmiralsStoreException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<admirals><admiral prioritizeActive=\"true\">"
                + "<name>Historical Fixture Admiral</name><faction>Federation</faction>"
                + admiralElements
                + "</admiral></admirals>";
        Files.writeString(tempDir.resolve("admirals.xml"), xml);
        return new AdmiralsStore();
    }
}
