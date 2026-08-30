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
package com.kor.admiralty;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Specifies the transitional process-wide application state holder.
 */
class AppTest {

    /**
     * Restores process-start state so this static seam remains isolated across tests.
     */
    @BeforeEach
    void resetApp() {
        App.resetForTesting();
    }

    /**
     * Verifies callers cannot observe partially initialized application state.
     */
    @Test
    void readingBeforeBootstrapThrows() {
        assertAll(
                () -> assertThrows(IllegalStateException.class, App::gameData),
                () -> assertThrows(IllegalStateException.class, App::admirals),
                () -> assertThrows(IllegalStateException.class, App::dataDir),
                () -> assertThrows(IllegalStateException.class, App::admiralsStore),
                () -> assertThrows(IllegalStateException.class, App::iconCache));
    }
}
