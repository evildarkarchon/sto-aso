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

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;

import java.util.*;

/**
 * Immutable, headless Ship Filter pairing one entry type with its supported
 * ordering type. Instances are created by {@link ShipFilters}; callers cannot
 * supply Ship extraction or comparison behavior.
 *
 * @param <E> entry type retained in projected results
 * @param <O> ordering type supported by the entry type
 */
public final class ShipFilter<E, O> {

    private static final Set<ShipFaction> CLASSIFIED_FACTIONS = Set.of(
            ShipFaction.Federation,
            ShipFaction.Klingon,
            ShipFaction.Romulan,
            ShipFaction.JemHadar,
            ShipFaction.Universal);
    private static final Set<Role> CLASSIFIED_ROLES = Set.of(Role.Eng, Role.Sci, Role.Tac);
    private static final Set<Tier> CLASSIFIED_TIERS = Set.of(
            Tier.SmallCraft,
            Tier.Tier1,
            Tier.Tier2,
            Tier.Tier3,
            Tier.Tier4,
            Tier.Tier5,
            Tier.Tier6);
    private static final Set<Rarity> CLASSIFIED_RARITIES = Set.of(
            Rarity.Common,
            Rarity.Uncommon,
            Rarity.Rare,
            Rarity.VeryRare,
            Rarity.UltraRare,
            Rarity.Epic);

    private final ShipFilterAdapter<E, O> adapter;
    private final O order;
    private final Set<ShipFaction> factions;
    private final Set<Role> roles;
    private final Set<Tier> tiers;
    private final Set<Rarity> rarities;
    private final DuplicatePolicy duplicatePolicy;

    /**
     * Creates an all-visible filter that preserves duplicate entries.
     *
     * @param adapter module-owned entry adapter
     * @param order   initial supported ordering
     */
    ShipFilter(ShipFilterAdapter<E, O> adapter, O order) {
        this(adapter, order, DuplicatePolicy.PRESERVE_ALL);
    }

    /**
     * Creates an all-visible filter with one module-owned duplicate policy.
     *
     * @param adapter         module-owned entry adapter
     * @param order           initial supported ordering
     * @param duplicatePolicy duplicate treatment selected by the named factory
     */
    ShipFilter(ShipFilterAdapter<E, O> adapter, O order, DuplicatePolicy duplicatePolicy) {
        this(
                adapter,
                order,
                CLASSIFIED_FACTIONS,
                CLASSIFIED_ROLES,
                CLASSIFIED_TIERS,
                CLASSIFIED_RARITIES,
                duplicatePolicy);
    }

    /**
     * Carries validated immutable criteria into one derived filter.
     *
     * @param adapter         module-owned entry adapter
     * @param order           supported ordering
     * @param factions        immutable allowed factions
     * @param roles           immutable allowed roles
     * @param tiers           immutable allowed tiers
     * @param rarities        immutable allowed rarities
     * @param duplicatePolicy module-owned duplicate treatment
     */
    private ShipFilter(
            ShipFilterAdapter<E, O> adapter,
            O order,
            Set<ShipFaction> factions,
            Set<Role> roles,
            Set<Tier> tiers,
            Set<Rarity> rarities,
            DuplicatePolicy duplicatePolicy) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.order = Objects.requireNonNull(order, "order");
        this.factions = factions;
        this.roles = roles;
        this.tiers = tiers;
        this.rarities = rarities;
        this.duplicatePolicy = Objects.requireNonNull(duplicatePolicy, "duplicatePolicy");
    }

    /**
     * Applies one dimension while explicitly passing through unclassified values.
     *
     * @param value            entry value in the dimension
     * @param classifiedValues values governed by the filter controls
     * @param allowedValues    currently allowed classified values
     * @param <T>              dimension value type
     * @return whether the value passes this dimension
     */
    private static <T> boolean allowed(T value, Set<T> classifiedValues, Set<T> allowedValues) {
        return !classifiedValues.contains(value) || allowedValues.contains(value);
    }

    /**
     * Validates and defensively copies one caller-owned allowed-value set.
     *
     * @param values       caller-owned set
     * @param argumentName public argument name used in failures
     * @param <T>          dimension value type
     * @return immutable set snapshot
     */
    private static <T> Set<T> copyAllowed(Set<T> values, String argumentName) {
        Objects.requireNonNull(values, argumentName);
        for (T value : values) {
            Objects.requireNonNull(value, argumentName + " value");
        }
        return Set.copyOf(values);
    }

    /**
     * Validates every canonical fact needed before sorting or filtering begins.
     *
     * @param ship canonical Ship extracted from an entry
     */
    private static void validate(Ship ship) {
        Objects.requireNonNull(ship.getFaction(), "ship faction");
        Objects.requireNonNull(ship.getRole(), "ship role");
        Objects.requireNonNull(ship.getTier(), "ship tier");
        Objects.requireNonNull(ship.getRarity(), "ship rarity");
        Objects.requireNonNull(ship.getName(), "ship name");
    }

    /**
     * Returns a new filter allowing the supplied classified Ship factions.
     * Historical unclassified values remain visible independently of this set.
     *
     * @param factions allowed faction values, defensively copied
     * @return derived immutable Ship Filter
     * @throws NullPointerException if the set or one of its values is null
     */
    public ShipFilter<E, O> allowingFactions(Set<ShipFaction> factions) {
        return new ShipFilter<E, O>(
                adapter,
                order,
                copyAllowed(factions, "factions"),
                roles,
                tiers,
                rarities,
                duplicatePolicy);
    }

    /**
     * Returns a new filter allowing the supplied classified Ship roles. Small
     * Craft remains controlled by its tier rather than by this set.
     *
     * @param roles allowed role values, defensively copied
     * @return derived immutable Ship Filter
     * @throws NullPointerException if the set or one of its values is null
     */
    public ShipFilter<E, O> allowingRoles(Set<Role> roles) {
        return new ShipFilter<E, O>(
                adapter,
                order,
                factions,
                copyAllowed(roles, "roles"),
                tiers,
                rarities,
                duplicatePolicy);
    }

    /**
     * Returns a new filter allowing the supplied classified Ship tiers.
     *
     * @param tiers allowed tier values, defensively copied
     * @return derived immutable Ship Filter
     * @throws NullPointerException if the set or one of its values is null
     */
    public ShipFilter<E, O> allowingTiers(Set<Tier> tiers) {
        return new ShipFilter<E, O>(
                adapter,
                order,
                factions,
                roles,
                copyAllowed(tiers, "tiers"),
                rarities,
                duplicatePolicy);
    }

    /**
     * Returns a new filter allowing the supplied classified Ship rarities.
     *
     * @param rarities allowed rarity values, defensively copied
     * @return derived immutable Ship Filter
     * @throws NullPointerException if the set or one of its values is null
     */
    public ShipFilter<E, O> allowingRarities(Set<Rarity> rarities) {
        return new ShipFilter<E, O>(
                adapter,
                order,
                factions,
                roles,
                tiers,
                copyAllowed(rarities, "rarities"),
                duplicatePolicy);
    }

    /**
     * Returns a new filter using one value from the ordering type paired with
     * this filter's entry type.
     *
     * @param order supported ordering value
     * @return derived immutable Ship Filter
     * @throws NullPointerException if {@code order} is null
     */
    public ShipFilter<E, O> withOrder(O order) {
        return new ShipFilter<E, O>(
                adapter,
                Objects.requireNonNull(order, "order"),
                factions,
                roles,
                tiers,
                rarities,
                duplicatePolicy);
    }

    /**
     * Projects a defensive snapshot into canonical presentation order. The
     * returned immutable list contains the exact supplied entry instances.
     *
     * @param entries caller-owned entries to project
     * @return immutable visible entries in presentation order
     * @throws NullPointerException if the collection, an entry, or required
     *                              canonical Ship facts are null
     */
    public List<E> project(Collection<? extends E> entries) {
        Objects.requireNonNull(entries, "entries");
        List<E> projection = new ArrayList<E>();
        for (E entry : entries) {
            E presentEntry = Objects.requireNonNull(entry, "entry");
            validate(Objects.requireNonNull(adapter.ship(presentEntry), "entry ship"));
            projection.add(presentEntry);
        }
        Comparator<E> comparator = adapter.comparator(order);
        projection.sort(comparator);
        projection.removeIf(entry -> !includes(adapter.ship(entry)));
        projection = applyDuplicatePolicy(projection);
        return List.copyOf(projection);
    }

    /**
     * Returns the classified factions allowed by this immutable filter for the
     * module-owned Swing controls.
     *
     * @return immutable allowed faction values
     */
    Set<ShipFaction> allowedFactions() {
        return factions;
    }

    /**
     * Returns the classified roles allowed by this immutable filter for the
     * module-owned Swing controls.
     *
     * @return immutable allowed role values
     */
    Set<Role> allowedRoles() {
        return roles;
    }

    /**
     * Returns the classified tiers allowed by this immutable filter for the
     * module-owned Swing controls.
     *
     * @return immutable allowed tier values
     */
    Set<Tier> allowedTiers() {
        return tiers;
    }

    /**
     * Returns the classified rarities allowed by this immutable filter for the
     * module-owned Swing controls.
     *
     * @return immutable allowed rarity values
     */
    Set<Rarity> allowedRarities() {
        return rarities;
    }

    /**
     * Resolves one displayed entry to the canonical Ship used by module-owned
     * Swing details and activation behavior.
     *
     * @param entry displayed entry
     * @return canonical Ship facts for the entry
     */
    Ship ship(E entry) {
        return adapter.ship(entry);
    }

    /**
     * Replaces the old TreeSet side effect with an explicit, stable collapse of
     * adjacent canonically equal Ship types. Sorting first guarantees canonical
     * presentation order while stable sorting makes the first input identity win.
     *
     * @param sortedEntries validated entries in presentation order
     * @return entries after this filter's internal duplicate policy
     */
    private List<E> applyDuplicatePolicy(List<E> sortedEntries) {
        if (duplicatePolicy == DuplicatePolicy.PRESERVE_ALL || sortedEntries.size() < 2) {
            return sortedEntries;
        }
        List<E> distinctEntries = new ArrayList<E>(sortedEntries.size());
        E previous = null;
        for (E entry : sortedEntries) {
            if (previous == null
                    || ShipEntryAdapter.CANONICAL_ORDER.compare(adapter.ship(previous), adapter.ship(entry)) != 0) {
                distinctEntries.add(entry);
                previous = entry;
            }
        }
        return distinctEntries;
    }

    /**
     * Tests all four dimensions against canonical Ship facts.
     *
     * @param ship validated canonical Ship
     * @return whether the entry remains visible
     */
    private boolean includes(Ship ship) {
        // None and Small Craft's Smc role are historical/non-control values and
        // intentionally pass through even when the corresponding allowed set is empty.
        return allowed(ship.getFaction(), CLASSIFIED_FACTIONS, factions)
                && allowed(ship.getRole(), CLASSIFIED_ROLES, roles)
                && allowed(ship.getTier(), CLASSIFIED_TIERS, tiers)
                && allowed(ship.getRarity(), CLASSIFIED_RARITIES, rarities);
    }
}
