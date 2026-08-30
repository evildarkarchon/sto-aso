/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Guards the package boundaries established by the GameData seam migration.
 */
class ArchitectureTest {

    /**
     * Verifies the deleted compatibility store cannot return and domain/io sources remain independent of Swing UI.
     *
     * @throws IOException if project sources cannot be scanned
     */
    @Test
    void legacyStoreIsAbsentAndLowerLayersDoNotImportUi() throws IOException {
        Path sourceRoot = Path.of("src", "com", "kor", "admiralty");

        assertAll(
                () -> assertFalse(Files.exists(sourceRoot.resolve("io/Datastore.java"))),
                () -> assertNoImport(sourceRoot.resolve("beans"), "com.kor.admiralty.ui"),
                () -> assertNoImport(sourceRoot.resolve("io"), "com.kor.admiralty.ui"));
    }

    /**
     * Scans Java sources beneath a package and rejects imports from a forbidden package.
     *
     * @param packageRoot package directory to scan
     * @param forbiddenPackage package prefix that must not be imported
     * @throws IOException if a source file cannot be read
     */
    private static void assertNoImport(Path packageRoot, String forbiddenPackage) throws IOException {
        try (Stream<Path> files = Files.walk(packageRoot)) {
            List<Path> javaFiles = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            for (Path file : javaFiles) {
                String source = Files.readString(file);
                assertFalse(
                        source.contains("import " + forbiddenPackage),
                        () -> file + " imports " + forbiddenPackage);
            }
        }
    }
}
