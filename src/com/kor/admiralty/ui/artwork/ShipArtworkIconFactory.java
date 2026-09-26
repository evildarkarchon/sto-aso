/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.resources.ShipIconFactory;

import javax.swing.ImageIcon;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Adapts the temporary five-argument renderer contract to application-owned Ship Artwork.
 * This bridge is removed when those renderers accept canonical Ships directly.
 */
public final class ShipArtworkIconFactory implements ShipIconFactory {

    private final ShipArtwork artwork;
    private final Map<Request, Ship> shipsByRequest;

    /**
     * Indexes the canonical Ships used by existing rendering call sites.
     *
     * @param gameData reference data that owns every Ship passed to artwork
     * @param artwork application-owned Ship Artwork module
     * @throws NullPointerException if either argument is null
     */
    public ShipArtworkIconFactory(GameData gameData, ShipArtwork artwork) {
        Objects.requireNonNull(gameData, "gameData");
        this.artwork = Objects.requireNonNull(artwork, "artwork");
        Map<Request, Ship> ships = new HashMap<>();
        for (Ship ship : gameData.ships()) {
            // The old interface cannot distinguish Ships with identical artwork facts.
            // Either canonical Ship produces the same composed pixels for this request.
            ships.putIfAbsent(Request.from(ship), ship);
        }
        shipsByRequest = Map.copyOf(ships);
    }

    /**
     * Resolves an existing renderer request to a canonical Ship and named presentation.
     *
     * @param iconName canonical Ship source-image filename
     * @param faction canonical Ship faction
     * @param role canonical Ship role
     * @param rarity canonical Ship rarity
     * @param owned {@code true} for specific reusable-Ship artwork, or {@code false}
     *              for deliberately generic artwork such as One-Time Ships
     * @return the module's immediate stable image handle
     * @throws IllegalArgumentException if the request does not identify a canonical Ship
     * @throws NullPointerException if any presentation fact is null
     * @throws IllegalStateException if Ship Artwork has already closed
     */
    @Override
    public ImageIcon getIcon(String iconName, ShipFaction faction, Role role, Rarity rarity, boolean owned) {
        Request request = new Request(
                Objects.requireNonNull(iconName, "iconName"),
                Objects.requireNonNull(faction, "faction"),
                Objects.requireNonNull(role, "role"),
                Objects.requireNonNull(rarity, "rarity"));
        Ship ship = shipsByRequest.get(request);
        if (ship == null) {
            throw new IllegalArgumentException("Unknown canonical Ship artwork request: " + iconName);
        }
        return artwork.forShip(ship,
                owned ? ShipArtwork.Presentation.SPECIFIC : ShipArtwork.Presentation.GENERIC);
    }

    /** Identifies the canonical artwork facts carried by one legacy renderer request. */
    private record Request(String iconName, ShipFaction faction, Role role, Rarity rarity) {

        /** Returns the request identity represented by a canonical Ship. */
        private static Request from(Ship ship) {
            return new Request(ship.getIconName(), ship.getFaction(), ship.getRole(), ship.getRarity());
        }
    }
}
