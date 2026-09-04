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

import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipUsageRow;
import com.kor.admiralty.enums.*;

import java.util.Objects;
import java.util.Set;

/**
 * Named factories for the supported Ship Filter entry and ordering pairs.
 */
public final class ShipFilters {

    private ShipFilters() {
    }

    /**
     * Creates the canonical all-visible Ship Filter.
     *
     * @return immutable Ship Filter using canonical Ship ordering
     */
    public static ShipFilter<Ship, ShipSortOrder> ships() {
        return new ShipFilter<Ship, ShipSortOrder>(ShipEntryAdapter.INSTANCE, ShipSortOrder.Default);
    }

    /**
     * Creates the established complete faction profile for one Admiral.
     *
     * @param faction Admiral faction selecting Federation- or Klingon-aligned
     *                visibility
     * @return immutable canonical Ship Filter with the complete faction profile
     * @throws NullPointerException if {@code faction} is null
     */
    public static ShipFilter<Ship, ShipSortOrder> shipsForAdmiral(PlayerFaction faction) {
        return ships().allowingFactions(factionsFor(faction));
    }

    /**
     * Creates the established One-Time Ship candidate profile for one Admiral.
     * Classified candidates must be faction-aligned and Tier 6; historical
     * unclassified tiers remain visible, and canonically equal Ship types are
     * presented once.
     *
     * @param faction Admiral faction selecting Federation- or Klingon-aligned
     *                visibility
     * @return immutable One-Time Ship candidate filter
     * @throws NullPointerException if {@code faction} is null
     */
    public static ShipFilter<Ship, ShipSortOrder> oneTimeShipsForAdmiral(PlayerFaction faction) {
        return new ShipFilter<Ship, ShipSortOrder>(
                ShipEntryAdapter.INSTANCE,
                ShipSortOrder.Default,
                DuplicatePolicy.CANONICAL_SHIP_TYPES)
                .allowingFactions(factionsFor(faction))
                .allowingTiers(Set.of(Tier.Tier6));
    }

    /**
     * Creates the canonical all-visible RosterCard filter. Distinct card
     * identities remain present and comparator-equal cards retain input order.
     *
     * @return immutable RosterCard filter using canonical Ship ordering
     */
    public static ShipFilter<RosterCard, ShipSortOrder> rosterCards() {
        return new ShipFilter<RosterCard, ShipSortOrder>(
                RosterCardEntryAdapter.INSTANCE,
                ShipSortOrder.Default);
    }

    /**
     * Creates the all-visible Ship usage filter in canonical default order.
     * Count-based orderings can be selected through the paired
     * {@link ShipUsageSortOrder} type.
     *
     * @return immutable Ship usage filter
     */
    public static ShipFilter<ShipUsageRow, ShipUsageSortOrder> usageRows() {
        return new ShipFilter<ShipUsageRow, ShipUsageSortOrder>(
                ShipUsageRowEntryAdapter.INSTANCE,
                ShipUsageSortOrder.Default);
    }

    /**
     * Resolves one Admiral faction to the canonical Ship factions that profile
     * permits. Jem'Hadar and Universal Ships occur in every profile.
     *
     * @param faction non-null Admiral faction
     * @return immutable allowed classified Ship factions
     */
    private static Set<ShipFaction> factionsFor(PlayerFaction faction) {
        return switch (Objects.requireNonNull(faction, "faction")) {
            case Federation, JemHadarFed -> Set.of(
                    ShipFaction.Federation,
                    ShipFaction.JemHadar,
                    ShipFaction.Universal);
            case Klingon, JemHadarKDF -> Set.of(
                    ShipFaction.Klingon,
                    ShipFaction.JemHadar,
                    ShipFaction.Universal);
            case RomulanFed -> Set.of(
                    ShipFaction.Federation,
                    ShipFaction.Romulan,
                    ShipFaction.JemHadar,
                    ShipFaction.Universal);
            case RomulanKDF -> Set.of(
                    ShipFaction.Klingon,
                    ShipFaction.Romulan,
                    ShipFaction.JemHadar,
                    ShipFaction.Universal);
        };
    }
}
