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

import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.swing.AbstractListModel;

import com.kor.admiralty.beans.Ship;

/**
 * Shared sorting and filtering for list entries that expose canonical Ship
 * facts.
 *
 * @param <T> list entry type
 * @param <S> sort-order type for that entry
 */
public abstract class AbstractShipListModel<T, S> extends AbstractListModel<T> {

    @Serial
    private static final long serialVersionUID = 5594846345013721430L;

    protected final List<T> entries;
    protected final BitSet included;
    protected int[] visible;
    protected S sortOrder;

    protected boolean showFederation = true;
    protected boolean showKlingon = true;
    protected boolean showRomulan = true;
    protected boolean showJemHadar = true;
    protected boolean showUniversal = true;

    protected boolean showEngineering = true;
    protected boolean showTactical = true;
    protected boolean showScience = true;

    protected boolean showSmallCraft = true;
    protected boolean showTier1 = true;
    protected boolean showTier2 = true;
    protected boolean showTier3 = true;
    protected boolean showTier4 = true;
    protected boolean showTier5 = true;
    protected boolean showTier6 = true;

    protected boolean showCommon = true;
    protected boolean showUncommon = true;
    protected boolean showRare = true;
    protected boolean showVeryRare = true;
    protected boolean showUltraRare = true;
    protected boolean showEpic = true;

    /**
     * Creates an empty model with its entry-specific default ordering.
     *
     * @param defaultSortOrder initial sort order
     */
    protected AbstractShipListModel(S defaultSortOrder) {
        this(List.of(), defaultSortOrder);
    }

    /**
     * Creates a model from caller-owned entries while preserving their initial
     * order until a sort or filter changes.
     *
     * @param entries          initial entries copied into the model
     * @param defaultSortOrder initial sort order
     * @throws NullPointerException if an argument or entry is null
     */
    protected AbstractShipListModel(Collection<T> entries, S defaultSortOrder) {
        Objects.requireNonNull(entries, "entries");
        this.entries = new ArrayList<T>(entries);
        this.entries.forEach(entry -> Objects.requireNonNull(entry, "entry"));
        included = new BitSet(entries.size());
        included.set(0, entries.size());
        visible = new int[entries.size()];
        for (int index = 0; index < visible.length; index++) {
            visible[index] = index;
        }
        sortOrder = Objects.requireNonNull(defaultSortOrder, "defaultSortOrder");
    }

    /**
     * Returns the canonical Ship whose facts drive filtering for one entry.
     *
     * @param entry list entry
     * @return canonical Ship facts
     */
    protected abstract Ship ship(T entry);

    /**
     * Returns the entry comparator for one selected sort mode.
     *
     * @param sortOrder selected sort order
     * @return comparator that reads only this model's entry type
     */
    protected abstract Comparator<T> comparator(S sortOrder);

    /**
     * Removes every entry and publishes the empty model state.
     */
    public void removeAllEntries() {
        entries.clear();
        visible = new int[0];
        updateIncluded();
    }

    /**
     * Replaces all model entries with a caller-owned collection snapshot.
     *
     * @param collection replacement entries
     */
    public void setEntries(Collection<T> collection) {
        Objects.requireNonNull(collection, "collection");
        entries.clear();
        addEntries(collection);
    }

    /**
     * Adds entries, then reapplies sorting and filtering to one coherent list-model
     * state.
     *
     * @param collection entries to add
     */
    public void addEntries(Collection<T> collection) {
        Objects.requireNonNull(collection, "collection");
        collection.forEach(entry -> Objects.requireNonNull(entry, "entry"));
        entries.addAll(collection);
        visible = new int[entries.size()];
        updateIncluded();
    }

    @Override
    public int getSize() {
        return included.cardinality();
    }

    @Override
    public T getElementAt(int index) {
        return entries.get(visible[index]);
    }

    /**
     * Returns the ordering currently applied to visible entries.
     *
     * @return current entry sort order
     */
    public S getSortOrder() {
        return sortOrder;
    }

    /**
     * Changes entry ordering and publishes the rebuilt visible model state.
     *
     * @param sortOrder replacement sort order
     */
    public void setSortOrder(S sortOrder) {
        this.sortOrder = Objects.requireNonNull(sortOrder, "sortOrder");
        updateIncluded();
    }

    /**
     * @return whether Federation entries pass the faction filter
     */
    public boolean isShowFederation() {
        return showFederation;
    }

    /**
     * Changes Federation visibility and publishes the rebuilt visible model state.
     *
     * @param showFederation whether Federation entries remain visible
     */
    public void setShowFederation(boolean showFederation) {
        this.showFederation = showFederation;
        updateIncluded();
    }

    /**
     * @return whether Klingon entries pass the faction filter
     */
    public boolean isShowKlingon() {
        return showKlingon;
    }

    /**
     * Changes Klingon visibility and publishes the rebuilt visible model state.
     *
     * @param showKlingon whether Klingon entries remain visible
     */
    public void setShowKlingon(boolean showKlingon) {
        this.showKlingon = showKlingon;
        updateIncluded();
    }

    /**
     * @return whether Romulan entries pass the faction filter
     */
    public boolean isShowRomulan() {
        return showRomulan;
    }

    /**
     * Changes Romulan visibility and publishes the rebuilt visible model state.
     *
     * @param showRomulan whether Romulan entries remain visible
     */
    public void setShowRomulan(boolean showRomulan) {
        this.showRomulan = showRomulan;
        updateIncluded();
    }

    /**
     * @return whether Jem'Hadar entries pass the faction filter
     */
    public boolean isShowJemHadar() {
        return showJemHadar;
    }

    /**
     * Changes Jem'Hadar visibility and publishes the rebuilt visible model state.
     *
     * @param showJemHadar whether Jem'Hadar entries remain visible
     */
    public void setShowJemHadar(boolean showJemHadar) {
        this.showJemHadar = showJemHadar;
        updateIncluded();
    }

    /**
     * @return whether Universal entries pass the faction filter
     */
    public boolean isShowUniversal() {
        return showUniversal;
    }

    /**
     * Changes Universal visibility and publishes the rebuilt visible model state.
     *
     * @param showUniversal whether Universal entries remain visible
     */
    public void setShowUniversal(boolean showUniversal) {
        this.showUniversal = showUniversal;
        updateIncluded();
    }

    /**
     * @return whether Engineering entries pass the role filter
     */
    public boolean isShowEngineering() {
        return showEngineering;
    }

    /**
     * Changes Engineering visibility and publishes the rebuilt visible model state.
     *
     * @param showEngineering whether Engineering entries remain visible
     */
    public void setShowEngineering(boolean showEngineering) {
        this.showEngineering = showEngineering;
        updateIncluded();
    }

    /**
     * @return whether Tactical entries pass the role filter
     */
    public boolean isShowTactical() {
        return showTactical;
    }

    /**
     * Changes Tactical visibility and publishes the rebuilt visible model state.
     *
     * @param showTactical whether Tactical entries remain visible
     */
    public void setShowTactical(boolean showTactical) {
        this.showTactical = showTactical;
        updateIncluded();
    }

    /**
     * @return whether Science entries pass the role filter
     */
    public boolean isShowScience() {
        return showScience;
    }

    /**
     * Changes Science visibility and publishes the rebuilt visible model state.
     *
     * @param showScience whether Science entries remain visible
     */
    public void setShowScience(boolean showScience) {
        this.showScience = showScience;
        updateIncluded();
    }

    /**
     * @return whether Small Craft entries pass the tier filter
     */
    public boolean isShowSmallCraft() {
        return showSmallCraft;
    }

    /**
     * Changes Small Craft visibility and publishes the rebuilt visible model state.
     *
     * @param showSmallCraft whether Small Craft entries remain visible
     */
    public void setShowSmallCraft(boolean showSmallCraft) {
        this.showSmallCraft = showSmallCraft;
        updateIncluded();
    }

    /**
     * @return whether Tier 1 entries pass the tier filter
     */
    public boolean isShowTier1() {
        return showTier1;
    }

    /**
     * Changes Tier 1 visibility and publishes the rebuilt visible model state.
     *
     * @param showTier1 whether Tier 1 entries remain visible
     */
    public void setShowTier1(boolean showTier1) {
        this.showTier1 = showTier1;
        updateIncluded();
    }

    /**
     * @return whether Tier 2 entries pass the tier filter
     */
    public boolean isShowTier2() {
        return showTier2;
    }

    /**
     * Changes Tier 2 visibility and publishes the rebuilt visible model state.
     *
     * @param showTier2 whether Tier 2 entries remain visible
     */
    public void setShowTier2(boolean showTier2) {
        this.showTier2 = showTier2;
        updateIncluded();
    }

    /**
     * @return whether Tier 3 entries pass the tier filter
     */
    public boolean isShowTier3() {
        return showTier3;
    }

    /**
     * Changes Tier 3 visibility and publishes the rebuilt visible model state.
     *
     * @param showTier3 whether Tier 3 entries remain visible
     */
    public void setShowTier3(boolean showTier3) {
        this.showTier3 = showTier3;
        updateIncluded();
    }

    /**
     * @return whether Tier 4 entries pass the tier filter
     */
    public boolean isShowTier4() {
        return showTier4;
    }

    /**
     * Changes Tier 4 visibility and publishes the rebuilt visible model state.
     *
     * @param showTier4 whether Tier 4 entries remain visible
     */
    public void setShowTier4(boolean showTier4) {
        this.showTier4 = showTier4;
        updateIncluded();
    }

    /**
     * @return whether Tier 5 entries pass the tier filter
     */
    public boolean isShowTier5() {
        return showTier5;
    }

    /**
     * Changes Tier 5 visibility and publishes the rebuilt visible model state.
     *
     * @param showTier5 whether Tier 5 entries remain visible
     */
    public void setShowTier5(boolean showTier5) {
        this.showTier5 = showTier5;
        updateIncluded();
    }

    /**
     * @return whether Tier 6 entries pass the tier filter
     */
    public boolean isShowTier6() {
        return showTier6;
    }

    /**
     * Changes Tier 6 visibility and publishes the rebuilt visible model state.
     *
     * @param showTier6 whether Tier 6 entries remain visible
     */
    public void setShowTier6(boolean showTier6) {
        this.showTier6 = showTier6;
        updateIncluded();
    }

    /**
     * @return whether Common entries pass the rarity filter
     */
    public boolean isShowCommon() {
        return showCommon;
    }

    /**
     * Changes Common visibility and publishes the rebuilt visible model state.
     *
     * @param showCommon whether Common entries remain visible
     */
    public void setShowCommon(boolean showCommon) {
        this.showCommon = showCommon;
        updateIncluded();
    }

    /**
     * @return whether Uncommon entries pass the rarity filter
     */
    public boolean isShowUncommon() {
        return showUncommon;
    }

    /**
     * Changes Uncommon visibility and publishes the rebuilt visible model state.
     *
     * @param showUncommon whether Uncommon entries remain visible
     */
    public void setShowUncommon(boolean showUncommon) {
        this.showUncommon = showUncommon;
        updateIncluded();
    }

    /**
     * @return whether Rare entries pass the rarity filter
     */
    public boolean isShowRare() {
        return showRare;
    }

    /**
     * Changes Rare visibility and publishes the rebuilt visible model state.
     *
     * @param showRare whether Rare entries remain visible
     */
    public void setShowRare(boolean showRare) {
        this.showRare = showRare;
        updateIncluded();
    }

    /**
     * @return whether Very Rare entries pass the rarity filter
     */
    public boolean isShowVeryRare() {
        return showVeryRare;
    }

    /**
     * Changes Very Rare visibility and publishes the rebuilt visible model state.
     *
     * @param showVeryRare whether Very Rare entries remain visible
     */
    public void setShowVeryRare(boolean showVeryRare) {
        this.showVeryRare = showVeryRare;
        updateIncluded();
    }

    /**
     * @return whether Ultra Rare entries pass the rarity filter
     */
    public boolean isShowUltraRare() {
        return showUltraRare;
    }

    /**
     * Changes Ultra Rare visibility and publishes the rebuilt visible model state.
     *
     * @param showUltraRare whether Ultra Rare entries remain visible
     */
    public void setShowUltraRare(boolean showUltraRare) {
        this.showUltraRare = showUltraRare;
        updateIncluded();
    }

    /**
     * @return whether Epic entries pass the rarity filter
     */
    public boolean isShowEpic() {
        return showEpic;
    }

    /**
     * Changes Epic visibility and publishes the rebuilt visible model state.
     *
     * @param showEpic whether Epic entries remain visible
     */
    public void setShowEpic(boolean showEpic) {
        this.showEpic = showEpic;
        updateIncluded();
    }

    /**
     * Applies every visible filter to canonical Ship facts shared by all entry
     * types.
     *
     * @param ship canonical Ship facts
     * @return {@code true} when the entry should remain visible
     */
    protected boolean include(Ship ship) {
        switch (ship.getFaction()) {
            case Federation:
                if (!showFederation)
                    return false;
                break;
            case Klingon:
                if (!showKlingon)
                    return false;
                break;
            case Romulan:
                if (!showRomulan)
                    return false;
                break;
            case JemHadar:
                if (!showJemHadar)
                    return false;
                break;
            case Universal:
                if (!showUniversal)
                    return false;
                break;
            default:
        }

        switch (ship.getRole()) {
            case Eng:
                if (!showEngineering)
                    return false;
                break;
            case Tac:
                if (!showTactical)
                    return false;
                break;
            case Sci:
                if (!showScience)
                    return false;
                break;
            default:
        }

        switch (ship.getTier()) {
            case SmallCraft:
                if (!showSmallCraft)
                    return false;
                break;
            case Tier1:
                if (!showTier1)
                    return false;
                break;
            case Tier2:
                if (!showTier2)
                    return false;
                break;
            case Tier3:
                if (!showTier3)
                    return false;
                break;
            case Tier4:
                if (!showTier4)
                    return false;
                break;
            case Tier5:
                if (!showTier5)
                    return false;
                break;
            case Tier6:
                if (!showTier6)
                    return false;
                break;
            default:
        }

        switch (ship.getRarity()) {
            case Common:
                if (!showCommon)
                    return false;
                break;
            case Uncommon:
                if (!showUncommon)
                    return false;
                break;
            case Rare:
                if (!showRare)
                    return false;
                break;
            case VeryRare:
                if (!showVeryRare)
                    return false;
                break;
            case UltraRare:
                if (!showUltraRare)
                    return false;
                break;
            case Epic:
                if (!showEpic)
                    return false;
                break;
            default:
        }
        return true;
    }

    /**
     * Rebuilds the visible index after sorting so callers never observe entries and
     * indexes from different states.
     */
    protected void updateIncluded() {
        entries.sort(comparator(sortOrder));
        if (visible.length != entries.size()) {
            visible = new int[entries.size()];
        }
        Arrays.fill(visible, -1);
        included.clear();
        int visibleCount = 0;
        for (int index = 0; index < entries.size(); index++) {
            boolean isIncluded = include(ship(entries.get(index)));
            included.set(index, isIncluded);
            if (isIncluded) {
                visible[visibleCount] = index;
                visibleCount++;
            }
        }
        fireContentsChanged(this, 0, Math.max(0, entries.size() - 1));
    }
}
