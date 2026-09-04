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
package com.kor.admiralty.ui.shipfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.beans.ShipUsageRow;
import com.kor.admiralty.enums.PlayerFaction;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.ShipSortOrder;
import com.kor.admiralty.enums.ShipUsageSortOrder;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.io.GameData;

/**
 * Specifies the immutable headless Ship Filter interface through its public
 * factories and projected results.
 */
class ShipFilterTest {

    /**
     * Creates canonical Ship facts with explicit filter dimensions.
     *
     * @param name    canonical Ship name
     * @param faction faction filter value
     * @param role    role filter value
     * @param tier    tier filter value
     * @param rarity  rarity filter value
     * @return canonical test Ship
     */
    private static Ship ship(
            String name,
            ShipFaction faction,
            Role role,
            Tier tier,
            Rarity rarity) {
        return new ShipImpl(
                faction,
                tier,
                rarity,
                role,
                name,
                10,
                20,
                30,
                RuleType.All.rewardBonus(0),
                "");
    }

    /**
     * Returns canonical names from a projected Ship list.
     *
     * @param ships projected Ships
     * @return names in projection order
     */
    private static List<String> names(List<Ship> ships) {
        return ships.stream().map(Ship::getName).toList();
    }

    /**
     * Returns the exact One-Time card identities for one canonical Ship.
     *
     * @param admiral owning Admiral
     * @param ship    canonical Ship shared by the cards
     * @return cards in Roster identity order
     */
    private static List<RosterCard> cardsFor(Admiral admiral, Ship ship) {
        return admiral.getRoster().getOneTimeCards().stream()
                .filter(card -> card.getShip() == ship)
                .toList();
    }

    /**
     * Returns canonical Ship names from projected usage rows.
     *
     * @param rows projected usage rows
     * @return names in projection order
     */
    private static List<String> usageNames(List<ShipUsageRow> rows) {
        return rows.stream().map(row -> row.ship().getName()).toList();
    }

    /**
     * Verifies the canonical factory retains entry identities without exposing a
     * mutable result.
     */
    @Test
    void canonicalShipsAreProjectedInImmutableCanonicalOrder() {
        Ship high = ship("Zulu", ShipFaction.Federation, Role.Tac, Tier.Tier6, Rarity.Epic);
        Ship low = ship("Alpha", ShipFaction.Klingon, Role.Eng, Tier.Tier1, Rarity.Common);
        ShipFilter<Ship, ShipSortOrder> filter = ShipFilters.ships();

        List<Ship> visible = filter.project(new ArrayList<Ship>(List.of(high, low)));

        assertSame(low, visible.get(0));
        assertSame(high, visible.get(1));
        assertThrows(UnsupportedOperationException.class, () -> visible.add(low));
    }

    /**
     * Verifies canonical ordering states every tie-breaker: tier, rarity, role,
     * and finally case-sensitive Ship name.
     */
    @Test
    void canonicalShipOrderingUsesEveryExplicitTieBreaker() {
        Ship tierOne = ship("Zulu", ShipFaction.Federation, Role.Tac, Tier.Tier1, Rarity.Epic);
        Ship common = ship("Zulu", ShipFaction.Federation, Role.Sci, Tier.Tier6, Rarity.Common);
        Ship engineeringAlpha = ship("Alpha", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Rare);
        Ship engineeringZulu = ship("Zulu", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Rare);
        Ship science = ship("Alpha", ShipFaction.Federation, Role.Sci, Tier.Tier6, Rarity.Rare);

        assertEquals(
                List.of(tierOne, common, engineeringAlpha, engineeringZulu, science),
                ShipFilters.ships().project(List.of(science, engineeringZulu, common, tierOne, engineeringAlpha)));
    }

    /**
     * Verifies values are inclusive within one dimension while all four
     * dimensions remain jointly restrictive.
     */
    @Test
    void filterValuesUseOrWithinDimensionsAndAndAcrossDimensions() {
        Ship federation = ship("Federation", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Epic);
        Ship romulan = ship("Romulan", ShipFaction.Romulan, Role.Eng, Tier.Tier6, Rarity.Epic);
        Ship wrongFaction = ship("Klingon", ShipFaction.Klingon, Role.Eng, Tier.Tier6, Rarity.Epic);
        Ship wrongRole = ship("Science", ShipFaction.Federation, Role.Sci, Tier.Tier6, Rarity.Epic);
        Ship wrongTier = ship("Tier Five", ShipFaction.Federation, Role.Eng, Tier.Tier5, Rarity.Epic);
        Ship wrongRarity = ship("Common", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common);
        ShipFilter<Ship, ShipSortOrder> filter = ShipFilters.ships()
                .allowingFactions(Set.of(ShipFaction.Federation, ShipFaction.Romulan))
                .allowingRoles(Set.of(Role.Eng))
                .allowingTiers(Set.of(Tier.Tier6))
                .allowingRarities(Set.of(Rarity.Epic));

        List<Ship> visible = filter.project(List.of(
                wrongRarity,
                romulan,
                wrongFaction,
                wrongTier,
                federation,
                wrongRole));

        assertSame(federation, visible.get(0));
        assertSame(romulan, visible.get(1));
    }

    /**
     * Verifies every Admiral receives the established complete faction profile
     * while historical unclassified factions remain visible.
     *
     * @param faction Admiral faction selecting the profile
     */
    @ParameterizedTest
    @EnumSource(PlayerFaction.class)
    void admiralFactoriesApplyEstablishedFactionProfiles(PlayerFaction faction) {
        List<Ship> candidates = List.of(
                ship("Federation", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common),
                ship("Klingon", ShipFaction.Klingon, Role.Eng, Tier.Tier6, Rarity.Common),
                ship("Romulan", ShipFaction.Romulan, Role.Eng, Tier.Tier6, Rarity.Common),
                ship("JemHadar", ShipFaction.JemHadar, Role.Eng, Tier.Tier6, Rarity.Common),
                ship("Universal", ShipFaction.Universal, Role.Eng, Tier.Tier6, Rarity.Common),
                ship("Historical", ShipFaction.None, Role.Eng, Tier.Tier6, Rarity.Common));
        List<String> expected = switch (faction) {
            case Federation, JemHadarFed -> List.of("Federation", "Historical", "JemHadar", "Universal");
            case Klingon, JemHadarKDF -> List.of("Historical", "JemHadar", "Klingon", "Universal");
            case RomulanFed -> List.of("Federation", "Historical", "JemHadar", "Romulan", "Universal");
            case RomulanKDF -> List.of("Historical", "JemHadar", "Klingon", "Romulan", "Universal");
        };

        assertEquals(expected, names(ShipFilters.shipsForAdmiral(faction).project(candidates)));
    }

    /**
     * Verifies the One-Time Ship factory installs faction and Tier-6 policy as
     * one profile and explicitly deduplicates canonically equal Ship types.
     */
    @Test
    void oneTimeFactoryCombinesProfileAndCanonicalTypeDeduplication() {
        Ship firstDuplicate = ship(
                "Duplicate",
                ShipFaction.Federation,
                Role.Eng,
                Tier.Tier6,
                Rarity.Common);
        Ship secondDuplicate = new ShipImpl(
                ShipFaction.Federation,
                Tier.Tier6,
                Rarity.Common,
                Role.Eng,
                "Duplicate",
                99,
                98,
                97,
                RuleType.All.rewardBonus(0),
                "");
        Ship historicalTier = ship(
                "Historical Tier",
                ShipFaction.Universal,
                Role.Eng,
                Tier.None,
                Rarity.Common);
        Ship allowed = ship("Zulu", ShipFaction.Universal, Role.Eng, Tier.Tier6, Rarity.Common);
        Ship wrongFaction = ship("Klingon", ShipFaction.Klingon, Role.Eng, Tier.Tier6, Rarity.Common);
        Ship wrongTier = ship("Tier Five", ShipFaction.Universal, Role.Eng, Tier.Tier5, Rarity.Common);
        Ship smallCraft = ship(
                "Small Craft",
                ShipFaction.Universal,
                Role.Smc,
                Tier.SmallCraft,
                Rarity.Common);

        List<Ship> visible = ShipFilters.oneTimeShipsForAdmiral(PlayerFaction.Federation).project(List.of(
                allowed,
                firstDuplicate,
                wrongFaction,
                secondDuplicate,
                smallCraft,
                historicalTier,
                wrongTier));

        assertEquals(List.of("Historical Tier", "Duplicate", "Zulu"), names(visible));
        assertSame(firstDuplicate, visible.get(1));
    }

    /**
     * Verifies the RosterCard factory adapts nested Ship facts internally and
     * stable ordering preserves every comparator-equal card identity.
     */
    @Test
    void rosterCardFactoryFiltersNestedShipsAndStablyRetainsEqualIdentities() {
        Ship excluded = ship("Excluded", ShipFaction.Federation, Role.Eng, Tier.Tier1, Rarity.Common);
        Ship repeated = ship("Repeated", ShipFaction.Federation, Role.Eng, Tier.Tier3, Rarity.Common);
        Ship high = ship("High", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common);
        Admiral admiral = new Admiral(GameData.builder().ships(List.of(excluded, repeated, high)).build());
        admiral.adjustOneTimeShipQuantity(excluded, 1);
        admiral.adjustOneTimeShipQuantity(repeated, 3);
        admiral.adjustOneTimeShipQuantity(high, 1);
        RosterCard excludedCard = cardsFor(admiral, excluded).getFirst();
        List<RosterCard> equalCards = cardsFor(admiral, repeated);
        RosterCard highCard = cardsFor(admiral, high).getFirst();
        List<RosterCard> input = List.of(
                highCard,
                equalCards.get(2),
                excludedCard,
                equalCards.get(0),
                equalCards.get(1));

        List<RosterCard> visible = ShipFilters.rosterCards()
                .allowingTiers(Set.of(Tier.Tier3, Tier.Tier6))
                .project(input);

        assertEquals(4, visible.size());
        assertSame(equalCards.get(2), visible.get(0));
        assertSame(equalCards.get(0), visible.get(1));
        assertSame(equalCards.get(1), visible.get(2));
        assertSame(highCard, visible.get(3));
    }

    /**
     * Verifies every usage ordering reads immutable row counts and resolves count
     * ties with the same canonical Ship comparator.
     */
    @Test
    void usageFactorySupportsAllOrdersWithCanonicalTieBreakers() {
        ShipUsageRow alpha = new ShipUsageRow(
                ship("Alpha", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common),
                0,
                true);
        ShipUsageRow beta = new ShipUsageRow(
                ship("Beta", ShipFaction.Klingon, Role.Eng, Tier.Tier1, Rarity.Common),
                Integer.MAX_VALUE,
                false);
        ShipUsageRow gamma = new ShipUsageRow(
                ship("Gamma", ShipFaction.Federation, Role.Eng, Tier.Tier1, Rarity.Common),
                5,
                true);
        ShipUsageRow delta = new ShipUsageRow(
                ship("Delta", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common),
                5,
                true);
        List<ShipUsageRow> input = List.of(alpha, beta, delta, gamma);
        ShipFilter<ShipUsageRow, ShipUsageSortOrder> filter = ShipFilters.usageRows();

        assertEquals(List.of("Beta", "Gamma", "Alpha", "Delta"), usageNames(filter.project(input)));
        assertEquals(
                List.of("Beta", "Gamma", "Delta", "Alpha"),
                usageNames(filter.withOrder(ShipUsageSortOrder.MostUsed).project(input)));
        assertEquals(
                List.of("Alpha", "Gamma", "Delta", "Beta"),
                usageNames(filter.withOrder(ShipUsageSortOrder.LeastUsed).project(input)));
        assertSame(beta, filter.withOrder(ShipUsageSortOrder.MostUsed).project(input).getFirst());
        assertEquals(List.of("Beta", "Gamma", "Alpha", "Delta"), usageNames(filter.project(input)));
    }

    /**
     * Verifies every classified value in each dimension is allowed before a
     * caller derives narrower criteria.
     */
    @Test
    void everyClassifiedFilterValueIsInitiallyAllowed() {
        List<Ship> ships = new ArrayList<Ship>();
        for (ShipFaction faction : List.of(
                ShipFaction.Federation,
                ShipFaction.Klingon,
                ShipFaction.Romulan,
                ShipFaction.JemHadar,
                ShipFaction.Universal)) {
            ships.add(ship("Faction " + faction.name(), faction, Role.Eng, Tier.Tier6, Rarity.Common));
        }
        for (Role role : List.of(Role.Eng, Role.Sci, Role.Tac)) {
            ships.add(ship("Role " + role.name(), ShipFaction.Federation, role, Tier.Tier6, Rarity.Common));
        }
        for (Tier tier : List.of(
                Tier.SmallCraft,
                Tier.Tier1,
                Tier.Tier2,
                Tier.Tier3,
                Tier.Tier4,
                Tier.Tier5,
                Tier.Tier6)) {
            Role role = tier == Tier.SmallCraft ? Role.Smc : Role.Eng;
            ships.add(ship("Tier " + tier.name(), ShipFaction.Federation, role, tier, Rarity.Common));
        }
        for (Rarity rarity : List.of(
                Rarity.Common,
                Rarity.Uncommon,
                Rarity.Rare,
                Rarity.VeryRare,
                Rarity.UltraRare,
                Rarity.Epic)) {
            ships.add(ship("Rarity " + rarity.name(), ShipFaction.Federation, Role.Eng, Tier.Tier6, rarity));
        }

        assertEquals(21, ShipFilters.ships().project(ships).size());
    }

    /**
     * Verifies an empty allowed set hides classified values only in its own
     * dimension while the corresponding historical value remains visible.
     */
    @Test
    void emptyAllowedSetsKeepHistoricalValuesVisible() {
        Ship classified = ship(
                "Classified",
                ShipFaction.Federation,
                Role.Eng,
                Tier.Tier6,
                Rarity.Common);
        Ship historicalFaction = ship(
                "Historical Faction",
                ShipFaction.None,
                Role.Eng,
                Tier.Tier6,
                Rarity.Common);
        Ship historicalRole = ship(
                "Historical Role",
                ShipFaction.Federation,
                Role.None,
                Tier.Tier6,
                Rarity.Common);
        Ship historicalTier = ship(
                "Historical Tier",
                ShipFaction.Federation,
                Role.Eng,
                Tier.None,
                Rarity.Common);
        Ship historicalRarity = ship(
                "Historical Rarity",
                ShipFaction.Federation,
                Role.Eng,
                Tier.Tier6,
                Rarity.None);

        assertEquals(
                List.of(historicalFaction),
                ShipFilters.ships().allowingFactions(Set.of()).project(List.of(classified, historicalFaction)));
        assertEquals(
                List.of(historicalRole),
                ShipFilters.ships().allowingRoles(Set.of()).project(List.of(classified, historicalRole)));
        assertEquals(
                List.of(historicalTier),
                ShipFilters.ships().allowingTiers(Set.of()).project(List.of(classified, historicalTier)));
        assertEquals(
                List.of(historicalRarity),
                ShipFilters.ships().allowingRarities(Set.of()).project(List.of(classified, historicalRarity)));
    }

    /**
     * Verifies Small Craft bypasses role criteria but remains a classified tier
     * controlled exclusively by the tier dimension.
     */
    @Test
    void smallCraftIsGovernedByTierRatherThanRole() {
        Ship smallCraft = ship(
                "Small Craft",
                ShipFaction.Federation,
                Role.Smc,
                Tier.SmallCraft,
                Rarity.Common);

        assertEquals(List.of(smallCraft), ShipFilters.ships().allowingRoles(Set.of()).project(List.of(smallCraft)));
        assertEquals(List.of(), ShipFilters.ships().allowingTiers(Set.of()).project(List.of(smallCraft)));
    }

    /**
     * Verifies ordinary Ship projection preserves comparator-equal duplicates and
     * their stable input identity order.
     */
    @Test
    void ordinaryShipFactoryPreservesDuplicateEntryIdentities() {
        Ship first = ship("Duplicate", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common);
        Ship second = new ShipImpl(
                ShipFaction.Federation,
                Tier.Tier6,
                Rarity.Common,
                Role.Eng,
                "Duplicate",
                99,
                98,
                97,
                RuleType.All.rewardBonus(0),
                "");

        List<Ship> visible = ShipFilters.ships().project(List.of(first, second));

        assertEquals(2, visible.size());
        assertSame(first, visible.get(0));
        assertSame(second, visible.get(1));
    }

    /**
     * Verifies entry collections and filter sets are copied and mutator methods
     * derive new filters without changing the source filter.
     */
    @Test
    void entriesAndFilterValuesAreDefensivelyCopied() {
        Ship federation = ship("Federation", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common);
        Ship klingon = ship("Klingon", ShipFaction.Klingon, Role.Eng, Tier.Tier6, Rarity.Common);
        ArrayList<Ship> entries = new ArrayList<Ship>(List.of(klingon, federation));
        EnumSet<ShipFaction> factions = EnumSet.of(ShipFaction.Federation);
        ShipFilter<Ship, ShipSortOrder> allShips = ShipFilters.ships();
        ShipFilter<Ship, ShipSortOrder> federationOnly = allShips.allowingFactions(factions);

        List<Ship> published = federationOnly.project(entries);
        entries.clear();
        factions.clear();
        factions.add(ShipFaction.Klingon);

        assertEquals(List.of(federation), published);
        assertEquals(List.of(federation), federationOnly.project(List.of(klingon, federation)));
        assertEquals(List.of(federation, klingon), allShips.project(List.of(klingon, federation)));
    }

    /**
     * Verifies public arguments and every canonical fact used for filtering or
     * ordering are rejected before projection.
     */
    @Test
    void nullArgumentsAndCanonicalFactsAreRejected() {
        ShipFilter<Ship, ShipSortOrder> filter = ShipFilters.ships();
        HashSet<ShipFaction> factionsWithNull = new HashSet<ShipFaction>();
        factionsWithNull.add(null);
        Ship nullFaction = ship("Null Faction", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common);
        Ship nullRole = ship("Null Role", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common);
        Ship nullTier = ship("Null Tier", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common);
        Ship nullRarity = ship("Null Rarity", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common);
        Ship nullName = ship("Null Name", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common);
        nullFaction.setFaction(null);
        nullRole.setRole(null);
        nullTier.setTier(null);
        nullRarity.setRarity(null);
        nullName.setName(null);

        assertThrows(NullPointerException.class, () -> filter.project(null));
        assertThrows(NullPointerException.class, () -> filter.project(Collections.singletonList(null)));
        assertThrows(NullPointerException.class, () -> filter.allowingFactions(null));
        assertThrows(NullPointerException.class, () -> filter.allowingFactions(factionsWithNull));
        assertThrows(NullPointerException.class, () -> filter.allowingRoles(null));
        assertThrows(NullPointerException.class, () -> filter.allowingTiers(null));
        assertThrows(NullPointerException.class, () -> filter.allowingRarities(null));
        assertThrows(NullPointerException.class, () -> filter.withOrder(null));
        assertThrows(NullPointerException.class, () -> ShipFilters.shipsForAdmiral(null));
        assertThrows(NullPointerException.class, () -> ShipFilters.oneTimeShipsForAdmiral(null));
        assertThrows(NullPointerException.class, () -> filter.project(List.of(nullFaction)));
        assertThrows(NullPointerException.class, () -> filter.project(List.of(nullRole)));
        assertThrows(NullPointerException.class, () -> filter.project(List.of(nullTier)));
        assertThrows(NullPointerException.class, () -> filter.project(List.of(nullRarity)));
        assertThrows(NullPointerException.class, () -> filter.project(List.of(nullName)));
    }
}
