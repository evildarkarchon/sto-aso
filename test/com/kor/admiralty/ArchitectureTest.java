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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     * Verifies Globals declares no Swing or AWT imports alongside shared constants.
     *
     * @throws IOException if the Globals source cannot be scanned
     */
    @Test
    void globalsDoesNotImportSwingOrAwt() throws IOException {
        Path globalsSource = Path.of("src", "com", "kor", "admiralty", "Globals.java");

        assertAll(
                () -> assertNoImport(globalsSource, "javax.swing"),
                () -> assertNoImport(globalsSource, "java.awt"));
    }

    /**
     * Scans one Java source file or every Java source beneath a directory for forbidden imports.
     *
     * @param sourcePath source file or directory to scan
     * @param forbiddenPackage package prefix that must not be imported
     * @throws IOException if a source file cannot be read
     */
    private static void assertNoImport(Path sourcePath, String forbiddenPackage) throws IOException {
        Pattern forbiddenImport = Pattern.compile(
                "(?m)^\\s*import\\s+(?:static\\s+)?"
                        + Pattern.quote(forbiddenPackage)
                        + "(?:\\.|;)");
        try (Stream<Path> files = Files.walk(sourcePath)) {
            List<Path> javaFiles = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            for (Path file : javaFiles) {
                String source = Files.readString(file);
                assertFalse(
                        forbiddenImport.matcher(source).find(),
                        () -> file + " imports " + forbiddenPackage);
            }
        }
    }
}
