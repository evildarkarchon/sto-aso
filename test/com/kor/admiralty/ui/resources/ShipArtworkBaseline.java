/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.resources;

import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.ShipFaction;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Captures the pre-migration artwork recipe without changing production code. */
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
        write(output.resolve("source.png"), source());
        write(output.resolve("generic.png"), atlas(false));
        write(output.resolve("specific.png"), atlas(true));
        IconCache cache = new IconCache(output);
        ImageIcon bundled = new ActualShipIconFactory(cache).getIcon("Class_F_Shuttle.png",
                ShipFaction.Federation, Role.Smc, Rarity.Common, true);
        write(output.resolve("bundled-shuttle.png"), (BufferedImage) bundled.getImage());
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
     * Captures every faction/role column and rarity row using the current public factories.
     *
     * @param specific whether to compose the synthetic source or generic artwork
     * @return lossless tile atlas of current pixels
     */
    private static BufferedImage atlas(boolean specific) {
        BufferedImage atlas = new BufferedImage(SIZE * FACTIONS.size() * ROLES.size(),
                SIZE * RARITIES.size(), BufferedImage.TYPE_INT_ARGB);
        GenericShipIconFactory generic = new GenericShipIconFactory();
        for (ShipFaction faction : FACTIONS) {
            for (Role role : ROLES) {
                for (Rarity rarity : RARITIES) {
                    ImageIcon icon = specific
                            ? ActualShipIconFactory.buildIcon(source(), faction, role, rarity)
                            : generic.getIcon("unused.png", faction, role, rarity, false);
                    BufferedImage tile = (BufferedImage) icon.getImage();
                    // Copy ARGB directly so transparent RGB channels are not lost to a second composition.
                    atlas.setRGB(column(faction, role) * SIZE, RARITIES.indexOf(rarity) * SIZE,
                            SIZE, SIZE, tile.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE), 0, SIZE);
                }
            }
        }
        return atlas;
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
