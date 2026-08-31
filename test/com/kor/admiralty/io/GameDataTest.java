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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Specifies the public GameData seam for directory-backed and in-memory
 * reference data.
 */
class GameDataTest {

    private static final List<String> FIXTURE_FILES = List.of(
            "ships.csv", "renamed.csv", "traits.csv", "events.csv", "assignments.csv");
    private static final String CURRENT_SHIP_NAME = "U.S.S. Enterprise";
    private static final String OLD_SHIP_NAME = "Enterprise Refit";
    private static final String TRAIT_NAME = "Emergency Weapon Cycle";

    @TempDir
    Path tempDir;

    private GameData gameData;

    /**
     * Creates a mutable Ship for exercising the in-memory GameData builder.
     *
     * @param name  Ship name
     * @param trait Starship Trait name
     * @return a mutable Ship with representative fixture stats
     */
    private static Ship ship(String name, String trait) {
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
                trait);
    }

    /**
     * Copies the complete fixture directory and loads it through the production
     * factory.
     *
     * @throws IOException           if a fixture cannot be copied
     * @throws GameDataLoadException if the copied fixture directory cannot be
     *                               loaded
     */
    @BeforeEach
    void loadFixtureGameData() throws IOException, GameDataLoadException {
        copyFixtureFilesExcept();
        gameData = GameData.load(tempDir);
    }

    /**
     * Verifies Ship lookup folds case without requiring callers to normalize names.
     */
    @Test
    void shipLookupFoldsCase() {
        Ship ship = gameData.ship("u.s.s. eNtErPrIsE");

        assertEquals(CURRENT_SHIP_NAME, ship.getName());
    }

    /**
     * Verifies a differently-cased old name resolves to the current Ship
     * internally.
     */
    @Test
    void shipLookupFollowsRenamedShip() {
        Ship ship = gameData.ship("eNtErPrIsE rEfIt");

        assertEquals(CURRENT_SHIP_NAME, ship.getName());
    }

    /**
     * Verifies an unknown Ship name has no reference-data match.
     */
    @Test
    void unknownShipLookupReturnsNull() {
        assertNull(gameData.ship("U.S.S. Definitely Not A Ship"));
    }

    /**
     * Verifies a missing required Ships file is reported as a checked load failure.
     *
     * @throws IOException if the incomplete temporary directory cannot be prepared
     */
    @Test
    void missingShipsFileThrowsCheckedLoadException() throws IOException {
        Path incompleteDirectory = Files.createDirectory(tempDir.resolve("missing-ships"));
        copyFixtureFilesExcept(incompleteDirectory, "ships.csv");

        GameDataLoadException exception = assertThrows(
                GameDataLoadException.class,
                () -> GameData.load(incompleteDirectory));

        assertTrue(exception.getMessage().contains("ships.csv"));
    }

    /**
     * Verifies a readable Ships file with no records cannot produce silently empty
     * GameData.
     *
     * @throws IOException if the temporary fixture cannot be replaced
     */
    @Test
    void emptyShipsFileThrowsCheckedLoadException() throws IOException {
        Files.writeString(
                tempDir.resolve("ships.csv"),
                "Faction,Tier,Name,Rarity,Type,Eng,Tac,Sci,Bonus,Trait\n");

        assertThrows(GameDataLoadException.class, () -> GameData.load(tempDir));
    }

    /**
     * Verifies one malformed Ship record fails the whole load instead of publishing
     * partial data.
     *
     * @throws IOException if the temporary fixture cannot be replaced
     */
    @Test
    void malformedShipRecordThrowsCheckedLoadException() throws IOException {
        Files.writeString(
                tempDir.resolve("ships.csv"),
                """
                        Faction,Tier,Name,Rarity,Type,Eng,Tac,Sci,Bonus,Trait
                        Federation,0,Class F Shuttle,Common,Smc,2,2,2,,
                        Federation,not-a-tier,U.S.S. Enterprise,Epic,Eng,50,50,50,,
                        """);

        GameDataLoadException exception = assertThrows(
                GameDataLoadException.class,
                () -> GameData.load(tempDir));

        assertEquals(tempDir.resolve("ships.csv"), exception.getPath());
    }

    /**
     * Verifies malformed quoting after a valid Ship record rejects the complete CSV
     * instead of publishing its prefix.
     *
     * @throws IOException if the temporary fixture cannot be replaced
     */
    @Test
    void malformedShipQuotingAfterValidRecordRejectsWholeFile() throws IOException {
        Files.writeString(
                tempDir.resolve("ships.csv"),
                """
                        Faction,Tier,Name,Rarity,Type,Eng,Tac,Sci,Bonus,Trait
                        Federation,0,Class F Shuttle,Common,Smc,2,2,2,,
                        Federation,6,"Unterminated Ship,Epic,Eng,50,50,50,,
                        """);

        GameDataLoadException exception = assertThrows(
                GameDataLoadException.class,
                () -> GameData.load(tempDir));

        assertEquals(tempDir.resolve("ships.csv"), exception.getPath());
    }

    /**
     * Verifies one malformed Event record fails the whole load instead of
     * publishing partial data.
     *
     * @throws IOException if the temporary fixture cannot be replaced
     */
    @Test
    void malformedEventRecordThrowsCheckedLoadException() throws IOException {
        Files.writeString(
                tempDir.resolve("events.csv"),
                """
                        Event,Eng,Tac,Sci,Crit,Reward
                        First Contact Day,0,10,0,5,None
                        Broken Event,not-eng,0,0,0,None
                        """);

        assertThrows(GameDataLoadException.class, () -> GameData.load(tempDir));
    }

    /**
     * Verifies one malformed Assignment record fails the whole load instead of
     * publishing partial data.
     *
     * @throws IOException if the temporary fixture cannot be replaced
     */
    @Test
    void malformedAssignmentRecordThrowsCheckedLoadException() throws IOException {
        Files.writeString(
                tempDir.resolve("assignments.csv"),
                """
                        Assignment,Rarity,Eng,Tac,Sci,Hours,Minutes
                        Chart the B'Tran Cluster,Uncommon,45,40,65,2,30
                        Broken Assignment,Rare,not-eng,0,0,1,0
                        """);

        assertThrows(GameDataLoadException.class, () -> GameData.load(tempDir));
    }

    /**
     * Verifies builder-made data has the same lookup, rename, and trait behavior as
     * CSV data.
     */
    @Test
    void builderMatchesDirectoryLoadedLookupBehavior() {
        Ship builtShip = ship(CURRENT_SHIP_NAME, TRAIT_NAME);
        String resolvedTrait = gameData.ship(CURRENT_SHIP_NAME).getTrait();
        GameData builtData = GameData.builder()
                .ships(List.of(builtShip))
                .renamedShips(Map.of(OLD_SHIP_NAME, CURRENT_SHIP_NAME))
                .traits(Map.of(TRAIT_NAME, resolvedTrait))
                .build();

        assertSame(builtShip, builtData.ship("u.s.s. enterprise"));
        assertSame(builtShip, builtData.ship("ENTERPRISE REFIT"));
        assertNull(builtData.ship("U.S.S. Definitely Not A Ship"));
        assertEquals(resolvedTrait, builtShip.getTrait());
    }

    /**
     * Verifies directory loading resolves a Ship's Starship Trait through the
     * supplied trait data.
     */
    @Test
    void loadedShipHasResolvedStarshipTrait() {
        String trait = gameData.ship(CURRENT_SHIP_NAME).getTrait();

        assertNotEquals(TRAIT_NAME, trait);
        assertTrue(trait.contains("Fires faster while Emergency Power to Weapons is active."));
    }

    /**
     * Verifies Starship Trait production and lookup use the same locale-stable key
     * under a Turkish default locale.
     *
     * @throws IOException           if the temporary fixture cannot be replaced
     * @throws GameDataLoadException if the complete fixture cannot be loaded
     */
    @Test
    void starshipTraitLookupIsStableUnderTurkishDefaultLocale() throws IOException, GameDataLoadException {
        Files.writeString(
                tempDir.resolve("traits.csv"),
                """
                        Trait,Attributes,Description
                        Improved Gravity Well,,Resolved trait description
                        """);
        Files.writeString(
                tempDir.resolve("ships.csv"),
                """
                        Faction,Tier,Name,Rarity,Type,Eng,Tac,Sci,Bonus,Trait
                        Federation,6,Locale Test Ship,Epic,Eng,50,50,50,,Improved Gravity Well
                        """);
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            GameData localizedData = GameData.load(tempDir);

            assertTrue(localizedData.ship("Locale Test Ship").getTrait().contains("Resolved trait description"));
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    /**
     * Verifies the bundled production data remains loadable and non-trivial.
     *
     * @throws GameDataLoadException if the bundled data cannot be loaded
     */
    @Test
    void bundledDataLoadsWithShipsEventsAndAssignments() throws GameDataLoadException {
        Path bundledDataDirectory = Path.of(System.getProperty("user.dir"), "data");

        GameData bundledData = GameData.load(bundledDataDirectory);

        assertTrue(bundledData.ships().size() > 100);
        assertFalse(bundledData.events().isEmpty());
        assertFalse(bundledData.assignments().isEmpty());
    }

    /**
     * Verifies bundled Windows punctuation survives the production UTF-8 decoding
     * path.
     *
     * @throws GameDataLoadException if the bundled data cannot be loaded
     */
    @Test
    void bundledDataPreservesWindowsPunctuation() throws GameDataLoadException {
        Path bundledDataDirectory = Path.of(System.getProperty("user.dir"), "data");

        GameData bundledData = GameData.load(bundledDataDirectory);

        assertTrue(bundledData.assignments().stream()
                .anyMatch(assignment -> assignment.getName()
                        .equals("Divert Comet Approaching Trade-Partner’s Planet")));
        assertEquals(
                "Majority – Minority",
                bundledData.ship("Tellarite Pralim Flight-Deck Assault Cruiser (T6)").getTrait());
        assertTrue(bundledData.ship("Advanced Light Cruiser (T6)").getTrait().contains("QeHpu’"));
        assertTrue(bundledData.ship("Jem'Hadar Light Battlecruiser [T6]").getTrait().contains("ally’s help"));
    }

    /**
     * Verifies a legacy Windows-1252 download is decoded explicitly rather than
     * with UTF-8 replacement characters.
     *
     * @throws IOException           if the temporary Assignment fixture cannot be
     *                               replaced
     * @throws GameDataLoadException if the complete fixture cannot be loaded
     */
    @Test
    void legacyWindows1252GameDataDecodesWithoutReplacement() throws IOException, GameDataLoadException {
        String assignmentName = "Divert Comet Approaching Trade-Partner’s Planet";
        String csv = "Assignment,Rarity,Eng,Tac,Sci,Hours,Minutes\n"
                + assignmentName + ",Uncommon,45,40,65,2,30\n";
        Files.write(tempDir.resolve("assignments.csv"), csv.getBytes(Charset.forName("windows-1252")));

        GameData legacyData = GameData.load(tempDir);

        assertEquals(assignmentName, legacyData.assignments().iterator().next().getName());
    }

    /**
     * Copies all fixture files except any names explicitly omitted.
     *
     * @param omittedFiles fixture filenames not to copy
     * @throws IOException if a fixture is absent or cannot be copied
     */
    private void copyFixtureFilesExcept(String... omittedFiles) throws IOException {
        copyFixtureFilesExcept(tempDir, omittedFiles);
    }

    /**
     * Copies fixture files into a caller-selected temporary directory.
     *
     * @param destination  temporary directory receiving the fixtures
     * @param omittedFiles fixture filenames not to copy
     * @throws IOException if a fixture is absent or cannot be copied
     */
    private void copyFixtureFilesExcept(Path destination, String... omittedFiles) throws IOException {
        List<String> omitted = List.of(omittedFiles);
        for (String filename : FIXTURE_FILES) {
            if (omitted.contains(filename)) {
                continue;
            }
            try (InputStream fixture = getClass().getResourceAsStream("/gamedata/" + filename)) {
                if (fixture == null) {
                    throw new IOException("Missing test fixture: " + filename);
                }
                Files.copy(fixture, destination.resolve(filename));
            }
        }
    }
}
