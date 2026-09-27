/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.resources;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.artwork.ShipArtwork;
import com.kor.admiralty.ui.artwork.ShipArtworkTestFixture;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Captures review candidates through one isolated, canonical Ship Artwork lifetime. */
public final class ShipArtworkBaseline {

    static final int SIZE = 64;
    // Explicit ordering keeps saved tile coordinates independent of future enum reordering.
    static final List<ShipFaction> FACTIONS = List.of(ShipFaction.None, ShipFaction.Federation,
            ShipFaction.Klingon, ShipFaction.Romulan, ShipFaction.JemHadar, ShipFaction.Universal);
    static final List<Role> ROLES = List.of(Role.None, Role.Eng, Role.Sci, Role.Tac, Role.Smc);
    static final List<Rarity> RARITIES = List.of(Rarity.None, Rarity.Common, Rarity.Uncommon,
            Rarity.Rare, Rarity.VeryRare, Rarity.UltraRare, Rarity.Epic);

    private ShipArtworkBaseline() {
    }

    /**
     * Writes review candidates to an explicit directory; tests never call this entry point.
     *
     * @param args one output directory, normally beneath build
     * @throws IOException if an output image cannot be written
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected an explicit output directory");
        }
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        BufferedImage source = source();
        write(output.resolve("source.png"), source);
        Path scratch = Files.createTempDirectory("ship-artwork-baseline-");
        try {
            try (BaselineSet baseline = open(scratch, source)) {
                write(output.resolve("generic.png"), atlas(baseline, ShipArtwork.Presentation.GENERIC));
                // The historical specific.png used a direct helper that skipped production source scaling.
                write(output.resolve("specific-smooth.png"), atlas(baseline, ShipArtwork.Presentation.SPECIFIC));
                write(output.resolve("bundled-shuttle.png"),
                        pixels(baseline.artwork().forShip(baseline.shuttle(), ShipArtwork.Presentation.SPECIFIC)));
            }
        } finally {
            // A fresh empty data directory keeps existing output archives outside this module's lifetime.
            Files.delete(scratch);
        }
    }

    /**
     * Supplies a deterministic non-square source with transparent and translucent pixels.
     * The pattern exposes background, scaling, and overlapping frame changes.
     *
     * @return synthetic source artwork, independent of user data and remote images
     */
    static BufferedImage source() {
        BufferedImage image = new BufferedImage(97, 83, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = x < 24 ? 0 : x < 64 ? 128 : 255;
                image.setRGB(x, y, (alpha << 24) | ((x * 255 / 96) << 16)
                        | ((y * 255 / 82) << 8) | 0x55);
            }
        }
        return image;
    }

    /**
     * Opens one offline module for every canonical presentation and a real bundled Shuttle.
     * The synthetic source is supplied through the module's internal bundled-resource seam.
     *
     * @param directory isolated artwork directory
     * @param source synthetic PNG decoded from the fixed baseline or generated for review
     * @return owned module and canonical Ships for all presentation combinations
     * @throws IOException if the runtime cannot encode the deterministic source PNG
     */
    static BaselineSet open(Path directory, BufferedImage source) throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        if (!ImageIO.write(source, "png", encoded)) {
            throw new IOException("No PNG writer for the synthetic Ship source");
        }
        byte[] sourceBytes = encoded.toByteArray();
        List<Ship> ships = new ArrayList<>();
        Map<Combination, Ship> byCombination = new HashMap<>();
        Map<String, byte[]> bundledSources = new HashMap<>();
        int index = 0;
        for (ShipFaction faction : FACTIONS) {
            for (Role role : ROLES) {
                for (Rarity rarity : RARITIES) {
                    Ship ship = ship("Artwork Characterization " + index++, faction, role, rarity);
                    ships.add(ship);
                    byCombination.put(new Combination(faction, role, rarity), ship);
                    bundledSources.put(ship.getIconName(), sourceBytes);
                }
            }
        }
        Ship shuttle = ship("Class F Shuttle", ShipFaction.Federation, Role.Smc, Rarity.Common);
        ships.add(shuttle);
        GameData gameData = GameData.builder().ships(ships).build();
        ShipArtwork artwork = ShipArtworkTestFixture.offline(directory, gameData, List.of(), bundledSources);
        return new BaselineSet(artwork, Map.copyOf(byCombination), shuttle);
    }

    /** Identifies one canonical presentation in the fixed atlas order. */
    private record Combination(ShipFaction faction, Role role, Rarity rarity) {
    }

    /** Owns the shared offline module and canonical Ships used by one baseline run. */
    record BaselineSet(ShipArtwork artwork, Map<Combination, Ship> ships, Ship shuttle)
            implements AutoCloseable {

        /** Resolves the canonical Ship for one faction, role, and rarity tile. */
        Ship ship(ShipFaction faction, Role role, Rarity rarity) {
            return ships.get(new Combination(faction, role, rarity));
        }

        /** Ends the one module lifetime after candidate generation or assertions finish. */
        @Override
        public void close() {
            artwork.close();
        }
    }

    /**
     * Captures every faction and role column and rarity row through the named presentation.
     *
     * @param baseline canonical Ships and owned module for this run
     * @param presentation generic or specific Ship Artwork request
     * @return lossless tile atlas of visible pixels
     */
    private static BufferedImage atlas(BaselineSet baseline, ShipArtwork.Presentation presentation) {
        BufferedImage atlas = new BufferedImage(SIZE * FACTIONS.size() * ROLES.size(),
                SIZE * RARITIES.size(), BufferedImage.TYPE_INT_ARGB);
        for (ShipFaction faction : FACTIONS) {
            for (Role role : ROLES) {
                for (Rarity rarity : RARITIES) {
                    BufferedImage tile = pixels(baseline.artwork().forShip(
                            baseline.ship(faction, role, rarity), presentation));
                    // Copy ARGB directly so transparent RGB channels are not lost to a second composition.
                    atlas.setRGB(column(faction, role) * SIZE, RARITIES.indexOf(rarity) * SIZE,
                            SIZE, SIZE, tile.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE), 0, SIZE);
                }
            }
        }
        return atlas;
    }

    /** Creates one distinct canonical Ship for a baseline tile or bundled lookup. */
    private static Ship ship(String name, ShipFaction faction, Role role, Rarity rarity) {
        return new ShipImpl(faction, Tier.Tier6, rarity, role, name, 0, 0, 0,
                RuleType.All.rewardBonus(0), "");
    }

    /** Gets a defensive image copy from the stable read-only handle. */
    private static BufferedImage pixels(ImageIcon icon) {
        return (BufferedImage) icon.getImage();
    }

    /** Returns the stable column for one faction and role in the recorded atlas. */
    static int column(ShipFaction faction, Role role) {
        return FACTIONS.indexOf(faction) * ROLES.size() + ROLES.indexOf(role);
    }

    /** Writes a PNG or fails explicitly if the runtime has no PNG encoder. */
    private static void write(Path path, BufferedImage image) throws IOException {
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("No PNG writer for " + path);
        }
    }
}
