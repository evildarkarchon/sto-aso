/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/**
 * Specifies that the standalone Trait Viewer defers all UI and data access
 * until its entry point runs.
 */
class TraitViewerTest {

    /**
     * Verifies loading the entry-point class does not construct a frame or read
     * unbootstrapped application state.
     */
    @Test
    void classInitializationDoesNotRequireApplicationBootstrap() {
        assertDoesNotThrow(() -> Class.forName(
                "com.kor.admiralty.ui.TraitViewer",
                true,
                TraitViewerTest.class.getClassLoader()));
    }
}
