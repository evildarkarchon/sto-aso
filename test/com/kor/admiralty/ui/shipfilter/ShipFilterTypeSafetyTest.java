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
package com.kor.admiralty.ui.shipfilter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles consumer snippets to prove factories pair each entry with only its
 * supported ordering type.
 */
class ShipFilterTypeSafetyTest {

    /**
     * Compiles one in-memory consumer against the test runtime classpath.
     *
     * @param outputDirectory compiler output directory
     * @param className       top-level consumer class name
     * @param source          complete Java source
     * @return whether compilation succeeded
     * @throws IOException if the compiler's standard file manager cannot close
     */
    private static boolean compiles(Path outputDirectory, String className, String source) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Ship Filter type safety requires the configured JDK compiler");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = List.of(
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-d",
                    outputDirectory.toString());
            return compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    List.of(new StringSource(className, source)))
                    .call();
        }
    }

    /**
     * Verifies valid declarations compile while crossing either factory's order
     * type is rejected by javac.
     *
     * @param outputDirectory isolated compiler output
     * @throws IOException if compiler resources cannot be closed
     */
    @Test
    void factoriesRejectUnsupportedEntryAndOrderingPairs(@TempDir Path outputDirectory) throws IOException {
        String imports = """
                import com.kor.admiralty.beans.Ship;
                import com.kor.admiralty.beans.ShipUsageRow;
                import com.kor.admiralty.enums.ShipSortOrder;
                import com.kor.admiralty.enums.ShipUsageSortOrder;
                import com.kor.admiralty.ui.shipfilter.ShipFilter;
                import com.kor.admiralty.ui.shipfilter.ShipFilters;
                """;
        String valid = imports + """
                final class ValidPairing {
                    void verify() {
                        ShipFilter<Ship, ShipSortOrder> ships = ShipFilters.ships();
                        ShipFilter<ShipUsageRow, ShipUsageSortOrder> usage = ShipFilters.usageRows();
                        ships.withOrder(ShipSortOrder.Default);
                        usage.withOrder(ShipUsageSortOrder.MostUsed);
                    }
                }
                """;
        String invalidShipOrder = imports + """
                final class InvalidShipOrder {
                    void verify() {
                        ShipFilters.ships().withOrder(ShipUsageSortOrder.MostUsed);
                    }
                }
                """;
        String invalidUsageOrder = imports + """
                final class InvalidUsageOrder {
                    void verify() {
                        ShipFilters.usageRows().withOrder(ShipSortOrder.Default);
                    }
                }
                """;

        assertTrue(compiles(outputDirectory, "ValidPairing", valid));
        assertFalse(compiles(outputDirectory, "InvalidShipOrder", invalidShipOrder));
        assertFalse(compiles(outputDirectory, "InvalidUsageOrder", invalidUsageOrder));
    }

    /**
     * Minimal in-memory source file consumed by the system Java compiler.
     */
    private static final class StringSource extends SimpleJavaFileObject {

        private final String source;

        /**
         * Creates source at the URI expected for one top-level class.
         *
         * @param className top-level class name
         * @param source    complete Java source
         */
        private StringSource(String className, String source) {
            super(URI.create("string:///" + className + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        /**
         * Returns the complete in-memory source to javac.
         *
         * @param ignoreEncodingErrors ignored because the source is already text
         * @return Java source characters
         */
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
