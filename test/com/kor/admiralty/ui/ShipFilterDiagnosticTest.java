/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui;

import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.beans.ShipImpl;
import com.kor.admiralty.enums.Rarity;
import com.kor.admiralty.enums.Role;
import com.kor.admiralty.enums.RuleType;
import com.kor.admiralty.enums.ShipFaction;
import com.kor.admiralty.enums.Tier;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the standalone diagnostic prints the complete headless projection.
 */
class ShipFilterDiagnosticTest {

    /**
     * Catches skipped first entries and output that follows input rather than
     * canonical Ship ordering.
     */
    @Test
    void printsEveryShipInCanonicalOrderAndTheTotal() {
        Ship high = ship("Alpha", Tier.Tier6);
        Ship low = ship("Zulu", Tier.Tier1);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            ShipFilterDiagnostic.printShips(List.of(high, low), output);
        }

        assertEquals(List.of("1: Zulu", "2: Alpha", "2/2 ships."),
                bytes.toString(StandardCharsets.UTF_8).lines().toList());
    }

    /**
     * Verifies the standalone diagnostic also reports an empty input cleanly.
     */
    @Test
    void printsZeroTotalForNoShips() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            ShipFilterDiagnostic.printShips(List.of(), output);
        }

        assertEquals(List.of("0/0 ships."),
                bytes.toString(StandardCharsets.UTF_8).lines().toList());
    }

    /**
     * Creates real canonical Ship facts whose tier determines diagnostic order.
     *
     * @param name displayed canonical Ship name
     * @param tier ordering dimension
     * @return canonical Ship with fixed remaining dimensions
     */
    private static Ship ship(String name, Tier tier) {
        return new ShipImpl(ShipFaction.Federation, tier, Rarity.Common, Role.Eng,
                name, 10, 20, 30, RuleType.All.rewardBonus(0), "");
    }
}
