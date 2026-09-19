/**
 * Copyright (C) 2015, 2019, 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.kor.admiralty.ui.artwork;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.ShipFaction;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Offline composition using eagerly decoded, required bundled artwork. */
final class ArtworkComposition {

    private static final int SPAN = 64;
    private final Map<String, BufferedImage> images;

    /**
     * Loads every required composition asset, closing each supplied stream.
     *
     * @param resources reader of filenames relative to the bundled image directory
     * @throws IllegalStateException if a required image is missing or unreadable
     */
    ArtworkComposition(Function<String, InputStream> resources) {
        Map<String, BufferedImage> loaded = new HashMap<>();
        for (String faction : new String[]{"fed", "kdf", "rom", "jh"}) {
            for (String layer : new String[]{"bkg", "eng", "tac", "sci", "smc"}) {
                String name = faction + "_" + layer + ".png";
                loaded.put(name, readRequired(resources, name));
            }
        }
        for (String name : new String[]{"lobi.png", "eng.png", "tac.png", "sci.png"}) {
            loaded.put(name, readRequired(resources, name));
        }
        for (String frame : new String[]{"eng", "tac", "sci", "smc", "uncommon",
                "rare", "veryrare", "ultrarare", "epic"}) {
            String name = "frame_" + frame + ".png";
            loaded.put(name, scaleFrame(readRequired(resources, name)));
        }
        images = Map.copyOf(loaded);
    }

    /**
     * Composes generic artwork from validated canonical Ship facts without I/O.
     *
     * @param ship Ship supplying faction and role
     * @return newly composed 64-pixel square image, owned by the caller
     */
    BufferedImage generic(Ship ship) {
        BufferedImage image = new BufferedImage(SPAN, SPAN, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            String faction = factionPrefix(ship.getFaction());
            graphics.drawImage(images.get(faction + "_bkg.png"), 0, 0, null);
            boolean universal = ship.getFaction() == ShipFaction.Universal
                    || ship.getFaction() == ShipFaction.None;
            String role = roleSuffix(ship.getRole());
            if (universal) {
                graphics.drawImage(images.get("lobi.png"), 0, 0, null);
                // Universal Small Craft historically have no role overlay.
                if (role != null && ship.getRole() != Role.Smc) {
                    graphics.drawImage(images.get(role + ".png"), 1, 1, null);
                }
            } else if (role != null) {
                graphics.drawImage(images.get(faction + "_" + role + ".png"), 0, 0, null);
            }
            // Generic presentation historically omits rarity frames entirely.
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * Composes specific artwork from decoded source pixels without I/O.
     *
     * @param ship Ship supplying faction, role, and rarity
     * @param source decoded specific Ship image, retained unchanged
     * @return newly composed 64-pixel square image, owned by the caller
     */
    BufferedImage specific(Ship ship, BufferedImage source) {
        BufferedImage image = new BufferedImage(SPAN, SPAN, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.drawImage(images.get(factionPrefix(ship.getFaction()) + "_bkg.png"),
                    0, 0, SPAN, SPAN, null);
            // Preserve the existing smooth source scaling separately from bicubic frames.
            graphics.drawImage(source.getScaledInstance(SPAN, SPAN, Image.SCALE_SMOOTH),
                    0, 0, SPAN, SPAN, null);
            String role = roleSuffix(ship.getRole());
            if (role != null) {
                graphics.drawImage(images.get("frame_" + role + ".png"), 0, 0, SPAN, SPAN, null);
            }
            String rarity = switch (ship.getRarity()) {
                case None, Common -> null;
                case Uncommon -> "uncommon";
                case Rare -> "rare";
                case VeryRare -> "veryrare";
                case UltraRare -> "ultrarare";
                case Epic -> "epic";
            };
            if (rarity != null) {
                graphics.drawImage(images.get("frame_" + rarity + ".png"), 0, 0, SPAN, SPAN, null);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /** Returns the bundled faction prefix, including the historical Federation fallback. */
    private static String factionPrefix(ShipFaction faction) {
        return switch (faction) {
            case Federation, Universal, None -> "fed";
            case Klingon -> "kdf";
            case Romulan -> "rom";
            case JemHadar -> "jh";
        };
    }

    /** Returns the bundled role suffix, or null when the Ship has no role overlay. */
    private static String roleSuffix(Role role) {
        return switch (role) {
            case Eng -> "eng";
            case Tac -> "tac";
            case Sci -> "sci";
            case Smc -> "smc";
            case None -> null;
        };
    }

    /** Decodes one required bundled resource and reports its filename on any read failure. */
    private static BufferedImage readRequired(Function<String, InputStream> resources, String name) {
        try (InputStream stream = resources.apply(name)) {
            if (stream == null) {
                throw new IOException("Missing bundled image");
            }
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                throw new IOException("Unreadable bundled image");
            }
            return image;
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot load required Ship Artwork resource: " + name, failure);
        }
    }

    /** Scales a frame with the established bicubic interpolation and alpha handling. */
    private static BufferedImage scaleFrame(BufferedImage source) {
        BufferedImage image = new BufferedImage(SPAN, SPAN, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, SPAN, SPAN, null);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
