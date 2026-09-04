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
package com.kor.admiralty.ui.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;

/**
 * Characterizes established Ship Filter visibility and canonical ordering
 * through the public Swing list-model interface.
 */
class ShipListModelCharacterizationTest {

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
     * Returns visible names through the model's consumer-facing index.
     *
     * @param model filtered Ship model
     * @return visible canonical names in presentation order
     */
    private static List<String> visibleNames(ShipListModel model) {
        return IntStream.range(0, model.getSize())
                .mapToObj(index -> model.getElementAt(index).getName())
                .toList();
    }

    /**
     * Replaces model entries so the production presentation sorting path is used.
     *
     * @param ships caller-owned candidate order
     * @return populated model
     */
    private static ShipListModel modelWith(Ship... ships) {
        ShipListModel model = new ShipListModel();
        model.setShips(List.of(ships));
        return model;
    }

    /**
     * Disables every classified faction before enabling one requested value.
     *
     * @param model   filtered Ship model
     * @param allowed sole classified faction to expose
     */
    private static void showOnlyFaction(ShipListModel model, ShipFaction allowed) {
        hideClassifiedFactions(model);
        switch (allowed) {
            case Federation -> model.setShowFederation(true);
            case Klingon -> model.setShowKlingon(true);
            case Romulan -> model.setShowRomulan(true);
            case JemHadar -> model.setShowJemHadar(true);
            case Universal -> model.setShowUniversal(true);
            case None -> throw new IllegalArgumentException("None is not a classified faction");
        }
    }

    /**
     * Disables every classified faction control.
     *
     * @param model filtered Ship model
     */
    private static void hideClassifiedFactions(ShipListModel model) {
        model.setShowFederation(false);
        model.setShowKlingon(false);
        model.setShowRomulan(false);
        model.setShowJemHadar(false);
        model.setShowUniversal(false);
    }

    /**
     * Disables every classified role before enabling one requested value.
     *
     * @param model   filtered Ship model
     * @param allowed sole classified role to expose
     */
    private static void showOnlyRole(ShipListModel model, Role allowed) {
        hideClassifiedRoles(model);
        switch (allowed) {
            case Eng -> model.setShowEngineering(true);
            case Sci -> model.setShowScience(true);
            case Tac -> model.setShowTactical(true);
            case None, Smc -> throw new IllegalArgumentException("Role is not a classified role filter value");
        }
    }

    /**
     * Disables every classified role control.
     *
     * @param model filtered Ship model
     */
    private static void hideClassifiedRoles(ShipListModel model) {
        model.setShowEngineering(false);
        model.setShowScience(false);
        model.setShowTactical(false);
    }

    /**
     * Disables every classified tier before enabling one requested value.
     *
     * @param model   filtered Ship model
     * @param allowed sole classified tier to expose
     */
    private static void showOnlyTier(ShipListModel model, Tier allowed) {
        hideClassifiedTiers(model);
        switch (allowed) {
            case SmallCraft -> model.setShowSmallCraft(true);
            case Tier1 -> model.setShowTier1(true);
            case Tier2 -> model.setShowTier2(true);
            case Tier3 -> model.setShowTier3(true);
            case Tier4 -> model.setShowTier4(true);
            case Tier5 -> model.setShowTier5(true);
            case Tier6 -> model.setShowTier6(true);
            case None -> throw new IllegalArgumentException("None is not a classified tier");
        }
    }

    /**
     * Disables every classified tier control.
     *
     * @param model filtered Ship model
     */
    private static void hideClassifiedTiers(ShipListModel model) {
        model.setShowSmallCraft(false);
        model.setShowTier1(false);
        model.setShowTier2(false);
        model.setShowTier3(false);
        model.setShowTier4(false);
        model.setShowTier5(false);
        model.setShowTier6(false);
    }

    /**
     * Disables every classified rarity before enabling one requested value.
     *
     * @param model   filtered Ship model
     * @param allowed sole classified rarity to expose
     */
    private static void showOnlyRarity(ShipListModel model, Rarity allowed) {
        hideClassifiedRarities(model);
        switch (allowed) {
            case Common -> model.setShowCommon(true);
            case Uncommon -> model.setShowUncommon(true);
            case Rare -> model.setShowRare(true);
            case VeryRare -> model.setShowVeryRare(true);
            case UltraRare -> model.setShowUltraRare(true);
            case Epic -> model.setShowEpic(true);
            case None -> throw new IllegalArgumentException("None is not a classified rarity");
        }
    }

    /**
     * Disables every classified rarity control.
     *
     * @param model filtered Ship model
     */
    private static void hideClassifiedRarities(ShipListModel model) {
        model.setShowCommon(false);
        model.setShowUncommon(false);
        model.setShowRare(false);
        model.setShowVeryRare(false);
        model.setShowUltraRare(false);
        model.setShowEpic(false);
    }

    /**
     * Supplies every faction controlled by the established filter UI.
     *
     * @return faction and expected visible Ship name
     */
    private static Stream<Arguments> classifiedFactions() {
        return Stream.of(
                arguments(ShipFaction.Federation, "Federation"),
                arguments(ShipFaction.Klingon, "Klingon"),
                arguments(ShipFaction.Romulan, "Romulan"),
                arguments(ShipFaction.JemHadar, "JemHadar"),
                arguments(ShipFaction.Universal, "Universal"));
    }

    /**
     * Supplies every role controlled by the established filter UI.
     *
     * @return role and expected visible Ship name
     */
    private static Stream<Arguments> classifiedRoles() {
        return Stream.of(
                arguments(Role.Eng, "Engineering"),
                arguments(Role.Sci, "Science"),
                arguments(Role.Tac, "Tactical"));
    }

    /**
     * Supplies every tier controlled by the established filter UI.
     *
     * @return tier and expected visible Ship name
     */
    private static Stream<Arguments> classifiedTiers() {
        return Stream.of(
                arguments(Tier.SmallCraft, "Small Craft"),
                arguments(Tier.Tier1, "Tier 1"),
                arguments(Tier.Tier2, "Tier 2"),
                arguments(Tier.Tier3, "Tier 3"),
                arguments(Tier.Tier4, "Tier 4"),
                arguments(Tier.Tier5, "Tier 5"),
                arguments(Tier.Tier6, "Tier 6"));
    }

    /**
     * Supplies every rarity controlled by the established filter UI.
     *
     * @return rarity and expected visible Ship name
     */
    private static Stream<Arguments> classifiedRarities() {
        return Stream.of(
                arguments(Rarity.Common, "Common"),
                arguments(Rarity.Uncommon, "Uncommon"),
                arguments(Rarity.Rare, "Rare"),
                arguments(Rarity.VeryRare, "Very Rare"),
                arguments(Rarity.UltraRare, "Ultra Rare"),
                arguments(Rarity.Epic, "Epic"));
    }

    /**
     * Verifies every classified filter value and an all-unclassified historical
     * entry are initially visible.
     */
    @Test
    void allClassifiedAndHistoricalValuesAreVisibleByDefault() {
        ShipListModel model = modelWith(
                ship("Federation Small Craft", ShipFaction.Federation, Role.Smc, Tier.SmallCraft, Rarity.Common),
                ship("Klingon Tier 1", ShipFaction.Klingon, Role.Eng, Tier.Tier1, Rarity.Uncommon),
                ship("Romulan Tier 2", ShipFaction.Romulan, Role.Sci, Tier.Tier2, Rarity.Rare),
                ship("JemHadar Tier 3", ShipFaction.JemHadar, Role.Tac, Tier.Tier3, Rarity.VeryRare),
                ship("Universal Tier 4", ShipFaction.Universal, Role.Eng, Tier.Tier4, Rarity.UltraRare),
                ship("Federation Tier 5", ShipFaction.Federation, Role.Sci, Tier.Tier5, Rarity.Epic),
                ship("Klingon Tier 6", ShipFaction.Klingon, Role.Tac, Tier.Tier6, Rarity.Common),
                ship("Historical", ShipFaction.None, Role.None, Tier.None, Rarity.None));

        assertEquals(
                Set.of(
                        "Federation Small Craft",
                        "Klingon Tier 1",
                        "Romulan Tier 2",
                        "JemHadar Tier 3",
                        "Universal Tier 4",
                        "Federation Tier 5",
                        "Klingon Tier 6",
                        "Historical"),
                new HashSet<String>(visibleNames(model)));
    }

    /**
     * Verifies each faction control projects only its requested classified value.
     *
     * @param allowed      sole allowed faction
     * @param expectedName expected visible Ship
     */
    @ParameterizedTest
    @MethodSource("classifiedFactions")
    void factionFilteringUsesCanonicalShipFacts(ShipFaction allowed, String expectedName) {
        ShipListModel model = modelWith(
                ship("Federation", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common),
                ship("Klingon", ShipFaction.Klingon, Role.Eng, Tier.Tier6, Rarity.Common),
                ship("Romulan", ShipFaction.Romulan, Role.Eng, Tier.Tier6, Rarity.Common),
                ship("JemHadar", ShipFaction.JemHadar, Role.Eng, Tier.Tier6, Rarity.Common),
                ship("Universal", ShipFaction.Universal, Role.Eng, Tier.Tier6, Rarity.Common));

        showOnlyFaction(model, allowed);

        assertEquals(List.of(expectedName), visibleNames(model));
    }

    /**
     * Verifies each role control projects only its requested classified value.
     *
     * @param allowed      sole allowed role
     * @param expectedName expected visible Ship
     */
    @ParameterizedTest
    @MethodSource("classifiedRoles")
    void roleFilteringUsesCanonicalShipFacts(Role allowed, String expectedName) {
        ShipListModel model = modelWith(
                ship("Engineering", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common),
                ship("Science", ShipFaction.Federation, Role.Sci, Tier.Tier6, Rarity.Common),
                ship("Tactical", ShipFaction.Federation, Role.Tac, Tier.Tier6, Rarity.Common));

        showOnlyRole(model, allowed);

        assertEquals(List.of(expectedName), visibleNames(model));
    }

    /**
     * Verifies each tier control projects only its requested classified value.
     *
     * @param allowed      sole allowed tier
     * @param expectedName expected visible Ship
     */
    @ParameterizedTest
    @MethodSource("classifiedTiers")
    void tierFilteringUsesCanonicalShipFacts(Tier allowed, String expectedName) {
        ShipListModel model = modelWith(
                ship("Small Craft", ShipFaction.Federation, Role.Smc, Tier.SmallCraft, Rarity.Common),
                ship("Tier 1", ShipFaction.Federation, Role.Eng, Tier.Tier1, Rarity.Common),
                ship("Tier 2", ShipFaction.Federation, Role.Eng, Tier.Tier2, Rarity.Common),
                ship("Tier 3", ShipFaction.Federation, Role.Eng, Tier.Tier3, Rarity.Common),
                ship("Tier 4", ShipFaction.Federation, Role.Eng, Tier.Tier4, Rarity.Common),
                ship("Tier 5", ShipFaction.Federation, Role.Eng, Tier.Tier5, Rarity.Common),
                ship("Tier 6", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common));

        showOnlyTier(model, allowed);

        assertEquals(List.of(expectedName), visibleNames(model));
    }

    /**
     * Verifies each rarity control projects only its requested classified value.
     *
     * @param allowed      sole allowed rarity
     * @param expectedName expected visible Ship
     */
    @ParameterizedTest
    @MethodSource("classifiedRarities")
    void rarityFilteringUsesCanonicalShipFacts(Rarity allowed, String expectedName) {
        ShipListModel model = modelWith(
                ship("Common", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common),
                ship("Uncommon", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Uncommon),
                ship("Rare", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Rare),
                ship("Very Rare", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.VeryRare),
                ship("Ultra Rare", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.UltraRare),
                ship("Epic", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Epic));

        showOnlyRarity(model, allowed);

        assertEquals(List.of(expectedName), visibleNames(model));
    }

    /**
     * Verifies allowed values within one dimension combine inclusively.
     */
    @Test
    void factionValuesCombineWithOr() {
        ShipListModel model = modelWith(
                ship("Federation", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common),
                ship("Klingon", ShipFaction.Klingon, Role.Eng, Tier.Tier6, Rarity.Common),
                ship("Romulan", ShipFaction.Romulan, Role.Eng, Tier.Tier6, Rarity.Common));
        showOnlyFaction(model, ShipFaction.Federation);

        model.setShowRomulan(true);

        assertEquals(List.of("Federation", "Romulan"), visibleNames(model));
    }

    /**
     * Verifies a Ship must satisfy the active faction, role, tier, and rarity
     * dimensions together.
     */
    @Test
    void filterDimensionsCombineWithAnd() {
        ShipListModel model = modelWith(
                ship("Exact Match", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Epic),
                ship("Wrong Faction", ShipFaction.Klingon, Role.Eng, Tier.Tier6, Rarity.Epic),
                ship("Wrong Role", ShipFaction.Federation, Role.Sci, Tier.Tier6, Rarity.Epic),
                ship("Wrong Tier", ShipFaction.Federation, Role.Eng, Tier.Tier5, Rarity.Epic),
                ship("Wrong Rarity", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Common));

        showOnlyFaction(model, ShipFaction.Federation);
        showOnlyRole(model, Role.Eng);
        showOnlyTier(model, Tier.Tier6);
        showOnlyRarity(model, Rarity.Epic);

        assertEquals(List.of("Exact Match"), visibleNames(model));
    }

    /**
     * Verifies a historical unclassified faction remains visible when every
     * classified faction is hidden.
     */
    @Test
    void unclassifiedFactionRemainsVisibleWhenClassifiedFactionsAreHidden() {
        ShipListModel model = modelWith(
                ship("Historical Faction", ShipFaction.None, Role.Eng, Tier.Tier6, Rarity.Common));

        hideClassifiedFactions(model);

        assertEquals(List.of("Historical Faction"), visibleNames(model));
    }

    /**
     * Verifies a historical unclassified role remains visible when every
     * classified role is hidden.
     */
    @Test
    void unclassifiedRoleRemainsVisibleWhenClassifiedRolesAreHidden() {
        ShipListModel model = modelWith(
                ship("Historical Role", ShipFaction.Federation, Role.None, Tier.Tier6, Rarity.Common));

        hideClassifiedRoles(model);

        assertEquals(List.of("Historical Role"), visibleNames(model));
    }

    /**
     * Verifies a historical unclassified tier remains visible when every
     * classified tier is hidden.
     */
    @Test
    void unclassifiedTierRemainsVisibleWhenClassifiedTiersAreHidden() {
        ShipListModel model = modelWith(
                ship("Historical Tier", ShipFaction.Federation, Role.Eng, Tier.None, Rarity.Common));

        hideClassifiedTiers(model);

        assertEquals(List.of("Historical Tier"), visibleNames(model));
    }

    /**
     * Verifies a historical unclassified rarity remains visible when every
     * classified rarity is hidden.
     */
    @Test
    void unclassifiedRarityRemainsVisibleWhenClassifiedRaritiesAreHidden() {
        ShipListModel model = modelWith(
                ship("Historical Rarity", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.None));

        hideClassifiedRarities(model);

        assertEquals(List.of("Historical Rarity"), visibleNames(model));
    }

    /**
     * Verifies Small Craft bypasses classified role controls and is governed by
     * the dedicated tier control.
     */
    @Test
    void smallCraftIsGovernedByTheTierFilter() {
        ShipListModel model = modelWith(
                ship("Small Craft", ShipFaction.Federation, Role.Smc, Tier.SmallCraft, Rarity.Common));
        model.setShowEngineering(false);
        model.setShowScience(false);
        model.setShowTactical(false);
        assertEquals(List.of("Small Craft"), visibleNames(model));

        model.setShowSmallCraft(false);

        assertEquals(List.of(), visibleNames(model));
    }

    /**
     * Verifies canonical ordering uses tier, rarity, role, and case-sensitive name
     * in that priority order.
     */
    @Test
    void canonicalOrderingUsesTierThenRarityThenRoleThenName() {
        ShipListModel model = modelWith(
                ship("Zulu Tactical", ShipFaction.Federation, Role.Tac, Tier.Tier6, Rarity.Epic),
                ship("Science", ShipFaction.Federation, Role.Sci, Tier.Tier6, Rarity.Epic),
                ship("Engineering", ShipFaction.Federation, Role.Eng, Tier.Tier6, Rarity.Epic),
                ship("Common", ShipFaction.Federation, Role.Tac, Tier.Tier6, Rarity.Common),
                ship("Tier One", ShipFaction.Federation, Role.Tac, Tier.Tier1, Rarity.Epic),
                ship("Alpha Tactical", ShipFaction.Federation, Role.Tac, Tier.Tier6, Rarity.Epic));

        assertEquals(
                List.of("Tier One", "Common", "Engineering", "Science", "Alpha Tactical", "Zulu Tactical"),
                visibleNames(model));
    }
}
