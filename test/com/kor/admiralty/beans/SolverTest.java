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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.kor.admiralty.beans;

import com.kor.admiralty.enums.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies bounded candidate retention without changing score or identity ordering. */
class SolverTest {

    /** Creates identity-distinct copies with equal statistics and no scoring bonus. */
    private static List<RosterCard> cards(int count, int stat) {
        Ship ship = new ShipImpl(ShipFaction.Federation, Tier.Tier6, Rarity.Common,
                Role.Eng, "Solver Ship", stat, stat, stat, RuleType.All.rewardBonus(0), "");
        UUID owner = UUID.randomUUID();
        List<RosterCard> cards = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            cards.add(new RosterCard(RosterCardId.create(owner), ship,
                    RosterCardKind.ONE_TIME, RosterState.ONE_TIME));
        }
        return cards;
    }

    /** Creates an Assignment whose three requirements are identical. */
    private static Assignment assignment(int stat) {
        Assignment assignment = new Assignment();
        assignment.setRequiredEng(stat);
        assignment.setRequiredTac(stat);
        assignment.setRequiredSci(stat);
        return assignment;
    }

    /** A late triple must displace the earlier singles and pairs when only one result is requested. */
    @Test
    void laterBetterCandidateReplacesEarlierCandidates() {
        List<RosterCard> cards = cards(4, 10);
        List<CompositeSolution> solutions = Solver.solve(assignment(30), null, null, cards, 1, 7L);

        assertEquals(1, solutions.size());
        assertEquals(0.0d, solutions.getFirst().getScore());
        assertArrayEquals(new int[]{2, 1, 0}, solutions.getFirst().getSolution(0).getShipIndexes());
        assertEquals(List.of(cards.get(2), cards.get(1), cards.get(0)),
                solutions.getFirst().getRosterCards());
        assertEquals(7L, solutions.getFirst().getPlanningRevision());
    }

    /** Empty and undersized candidate sets must respect the requested result count. */
    @Test
    void emptyAndUndersizedSearchesReturnOnlyAvailableCandidates() {
        assertTrue(Solver.solve(assignment(10), null, null, cards(4, 0), 0, 0L).isEmpty());
        assertTrue(Solver.solve(assignment(10), null, null, List.of(), 10, 0L).isEmpty());
        assertTrue(Solver.solve(null, null, null, cards(4, 0), 10, 0L).isEmpty());
        List<CompositeSolution> solutions = Solver.solve(assignment(10), null, null, cards(2, 0), 10, 0L);
        assertEquals(3, solutions.size());
        assertArrayEquals(new int[]{0, -1, -1}, solutions.get(0).getSolution(0).getShipIndexes());
        assertArrayEquals(new int[]{1, -1, -1}, solutions.get(1).getSolution(0).getShipIndexes());
        assertArrayEquals(new int[]{1, 0, -1}, solutions.get(2).getSolution(0).getShipIndexes());
    }

    /**
     * Keeping all 1,333,500 candidates exceeds this heap; retaining ten must complete
     * and include later-enumerated ties in their stable index order.
     *
     * @param temporaryDirectory directory for the child JVM's captured output
     * @throws Exception if the child JVM cannot be started or its output cannot be read
     */
    @Test
    void largeRosterRetainsTopTenWithinSmallHeap(@TempDir Path temporaryDirectory) throws Exception {
        Path output = temporaryDirectory.resolve("solver-output.txt");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xmx64m", "-Djava.awt.headless=true", "-cp",
                System.getProperty("java.class.path"), SmallHeapProbe.class.getName())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Solver did not finish within 60 seconds");
            String result = Files.readString(output);
            assertEquals(0, process.exitValue(), result);
            assertEquals(List.of(
                    "[0, -1, -1]", "[1, -1, -1]", "[1, 0, -1]", "[2, -1, -1]",
                    "[2, 0, -1]", "[2, 1, -1]", "[2, 1, 0]", "[3, -1, -1]",
                    "[3, 0, -1]", "[3, 1, -1]"),
                    result.lines().filter(line -> line.startsWith("[")).toList());
        } finally {
            // A failing or interrupted test must not leave a solver JVM running.
            process.destroyForcibly();
            process.waitFor();
        }
    }

    /** Runs the real solver in an isolated heap without risking the test runner's heap. */
    public static class SmallHeapProbe {
        /** Prints the selected indexes for a Roster whose every combination has the same score. */
        public static void main(String[] args) {
            for (CompositeSolution solution : Solver.solve(assignment(10), null, null, cards(200, 0), 10, 0L)) {
                System.out.println(Arrays.toString(solution.getSolution(0).getShipIndexes()));
            }
        }
    }
}
