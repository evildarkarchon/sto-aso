/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the architectural seams established by the GameData migration and the
 * sole Admiral workspace cutover.
 */
class ArchitectureTest {

    /**
     * Keeps mutable Assignment retention and binding out of the editor while
     * allowing the workspace root and real Swing control listeners to own them.
     */
    @Test
    void assignmentEditorCannotRetainOrDirectlyBindMutableAssignment() {
        Class<?> editor = com.kor.admiralty.ui.AssignmentPanel.class;
        Pattern mutableAssignment = Pattern.compile("\\bcom\\.kor\\.admiralty\\.beans\\.Assignment\\b");
        Stream<String> signatures = Stream.concat(
                Arrays.stream(editor.getDeclaredFields()).map(field -> field.getGenericType().getTypeName()),
                Stream.concat(
                        Arrays.stream(editor.getDeclaredConstructors()).map(constructor -> constructor.toGenericString()),
                        Arrays.stream(editor.getDeclaredMethods()).map(method -> method.toGenericString())));
        List<String> mutableBindings = signatures
                .filter(signature -> mutableAssignment.matcher(signature).find()).toList();

        assertAll(
                () -> assertEquals(List.of(), mutableBindings, "Only the root may bind mutable Assignment"),
                () -> assertFalse(java.beans.PropertyChangeListener.class.isAssignableFrom(editor),
                        "The editor must not be a model property-change listener"));
    }

    /**
     * Lists Java sources at or beneath one file-system path while closing the
     * traversal stream before returning.
     *
     * @param sourcePath Java source file or directory
     * @return stable list of discovered Java source paths
     * @throws IOException if the source path cannot be walked
     */
    private static List<Path> javaSourcesUnder(Path sourcePath) throws IOException {
        try (Stream<Path> files = Files.walk(sourcePath)) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Scans one Java source file or every Java source beneath a directory for
     * forbidden imports.
     *
     * @param sourcePath       source file or directory to scan
     * @param forbiddenPackage package prefix that must not be imported
     * @throws IOException if a source file cannot be read
     */
    private static void assertNoImport(Path sourcePath, String forbiddenPackage) throws IOException {
        Pattern forbiddenImport = Pattern.compile(
                "(?m)^\\s*import\\s+(?:static\\s+)?"
                        + Pattern.quote(forbiddenPackage)
                        + "(?:\\.|;)");
        for (Path file : javaSourcesUnder(sourcePath)) {
            String source = Files.readString(file);
            assertFalse(
                    forbiddenImport.matcher(source).find(),
                    () -> file + " imports " + forbiddenPackage);
        }
    }

    /**
     * Scans production Java sources and rejects a package import from every file
     * except the named owner.
     *
     * @param sourceRoot      source directory to scan recursively
     * @param confinedPackage package prefix owned by one implementation file
     * @param allowedFile     sole source file permitted to import the package
     * @throws IOException if a source file cannot be read
     */
    private static void assertOnlyFileImports(
            Path sourceRoot,
            String confinedPackage,
            Path allowedFile) throws IOException {
        for (Path file : javaSourcesUnder(sourceRoot)) {
            if (!file.equals(allowedFile)) {
                assertNoImport(file, confinedPackage);
            }
        }
    }

    /**
     * Counts production sources declaring one Java type name.
     * Matching declarations rather than filenames also rejects package-private
     * legacy types hidden in an otherwise valid source file.
     *
     * @param sources  Java source files to scan
     * @param typeName simple type name to locate
     * @return number of source files containing a matching type declaration
     * @throws IOException if a source file cannot be read
     */
    private static long countSourcesDeclaringType(List<Path> sources, String typeName) throws IOException {
        Pattern declaration = Pattern.compile(
                "(?m)^\\s*(?:(?:public|protected|private|abstract|static|final|sealed|non-sealed)\\s+)*"
                        + "(?:class|interface|record|enum)\\s+"
                        + Pattern.quote(typeName)
                        + "\\b");
        long declarations = 0;
        for (Path source : sources) {
            if (declaration.matcher(Files.readString(source)).find()) {
                declarations++;
            }
        }
        return declarations;
    }

    /**
     * Removes comments and literals before searching for same-package type names,
     * so domain prose such as "GameData" cannot create a false source edge.
     * Newlines are preserved to keep diagnostics understandable.
     *
     * @param source complete Java source text
     * @return source-shaped text containing only code tokens and whitespace
     */
    private static String codeTokensOnly(String source) {
        StringBuilder code = new StringBuilder(source.length());
        JavaSourceRegion region = JavaSourceRegion.CODE;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';

            if (region == JavaSourceRegion.LINE_COMMENT) {
                code.append(current == '\n' ? '\n' : ' ');
                if (current == '\n') {
                    region = JavaSourceRegion.CODE;
                }
            } else if (region == JavaSourceRegion.BLOCK_COMMENT) {
                code.append(current == '\n' ? '\n' : ' ');
                if (current == '*' && next == '/') {
                    code.append(' ');
                    index++;
                    region = JavaSourceRegion.CODE;
                }
            } else if (region == JavaSourceRegion.STRING || region == JavaSourceRegion.CHARACTER) {
                code.append(current == '\n' ? '\n' : ' ');
                if (current == '\\' && next != '\0') {
                    code.append(next == '\n' ? '\n' : ' ');
                    index++;
                } else if ((region == JavaSourceRegion.STRING && current == '"')
                        || (region == JavaSourceRegion.CHARACTER && current == '\'')) {
                    region = JavaSourceRegion.CODE;
                }
            } else if (region == JavaSourceRegion.TEXT_BLOCK) {
                code.append(current == '\n' ? '\n' : ' ');
                if (current == '"' && source.startsWith("\"\"\"", index)) {
                    code.append("  ");
                    index += 2;
                    region = JavaSourceRegion.CODE;
                }
            } else if (current == '/' && next == '/') {
                code.append("  ");
                index++;
                region = JavaSourceRegion.LINE_COMMENT;
            } else if (current == '/' && next == '*') {
                code.append("  ");
                index++;
                region = JavaSourceRegion.BLOCK_COMMENT;
            } else if (current == '"' && source.startsWith("\"\"\"", index)) {
                code.append("   ");
                index += 2;
                region = JavaSourceRegion.TEXT_BLOCK;
            } else if (current == '"') {
                code.append(' ');
                region = JavaSourceRegion.STRING;
            } else if (current == '\'') {
                code.append(' ');
                region = JavaSourceRegion.CHARACTER;
            } else {
                code.append(current);
            }
        }
        return code.toString();
    }

    /**
     * Finds every project source reachable through imports and same-package type
     * references from one root source. The walk deliberately over-approximates
     * Java compilation so newly introduced helpers cannot hide an App import.
     *
     * @param sourceRoot production source root containing the top-level package
     * @param rootSource initial workspace source
     * @return immutable set of reachable project Java sources, including the root
     * @throws IOException if project sources cannot be scanned
     */
    private static Set<Path> reachableProjectSources(Path sourceRoot, Path rootSource) throws IOException {
        return reachableProjectSources(sourceRoot, rootSource, Set.of());
    }

    /**
     * Walks project dependencies while treating established domain packages and
     * explicitly allowed external types as terminal values. This checks ownership of new module
     * helpers without imposing new rules on existing domain implementations.
     *
     * @param sourceRoot production source root
     * @param rootSource initial source to traverse
     * @param terminalPackages package or source paths whose implementation is outside this check
     * @return immutable reachable sources, including terminal dependencies
     * @throws IOException if project sources cannot be read
     */
    private static Set<Path> reachableProjectSources(
            Path sourceRoot, Path rootSource, Set<Path> terminalPackages) throws IOException {
        Map<String, Path> sourcesByType = new HashMap<String, Path>();
        Map<Path, String> typesBySource = new HashMap<Path, String>();
        Map<String, List<Path>> sourcesByPackage = new HashMap<String, List<Path>>();
        for (Path source : javaSourcesUnder(sourceRoot)) {
            String typeName = sourceRoot.relativize(source)
                    .toString()
                    .replace(source.getFileSystem().getSeparator(), ".")
                    .replaceFirst("\\.java$", "");
            sourcesByType.put(typeName, source);
            typesBySource.put(source, typeName);
            String packageName = typeName.substring(0, typeName.lastIndexOf('.'));
            sourcesByPackage.computeIfAbsent(packageName, ignored -> new java.util.ArrayList<Path>())
                    .add(source);
        }

        Pattern importedType = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([\\w.]+(?:\\*)?)\\s*;");
        Set<Path> reachable = new HashSet<Path>();
        Deque<Path> pending = new ArrayDeque<Path>();
        reachable.add(rootSource);
        pending.add(rootSource);
        while (!pending.isEmpty()) {
            Path source = pending.removeFirst();
            if (terminalPackages.stream().anyMatch(source::startsWith)) {
                continue;
            }
            String contents = Files.readString(source);
            String codeContents = codeTokensOnly(contents);
            java.util.regex.Matcher imports = importedType.matcher(codeContents);
            while (imports.find()) {
                String importedName = imports.group(1);
                if (importedName.endsWith(".*")) {
                    importedName = importedName.substring(0, importedName.length() - 2);
                    for (Path importedSource : sourcesByPackage.getOrDefault(importedName, List.of())) {
                        String simpleName = importedSource.getFileName().toString().replaceFirst("\\.java$", "");
                        if (Pattern.compile("\\b" + Pattern.quote(simpleName) + "\\b").matcher(codeContents).find()
                                && reachable.add(importedSource)) {
                            pending.add(importedSource);
                        }
                    }
                }
                while (importedName.contains(".")) {
                    Path importedSource = sourcesByType.get(importedName);
                    if (importedSource != null) {
                        if (reachable.add(importedSource)) {
                            pending.add(importedSource);
                        }
                        break;
                    }
                    importedName = importedName.substring(0, importedName.lastIndexOf('.'));
                }
            }

            // Fully qualified uses need no import, but must not let an escaped
            // implementation helper disappear from the module boundary check.
            for (Map.Entry<String, Path> candidate : sourcesByType.entrySet()) {
                if (Pattern.compile("\\b" + Pattern.quote(candidate.getKey()) + "\\b")
                        .matcher(codeContents).find() && reachable.add(candidate.getValue())) {
                    pending.add(candidate.getValue());
                }
            }

            String sourceType = typesBySource.get(source);
            String packageName = sourceType.substring(0, sourceType.lastIndexOf('.'));
            for (Path candidate : sourcesByPackage.getOrDefault(packageName, List.of())) {
                if (candidate.equals(source)) {
                    continue;
                }
                String simpleName = candidate.getFileName().toString().replaceFirst("\\.java$", "");
                if (Pattern.compile("\\b" + Pattern.quote(simpleName) + "\\b").matcher(codeContents).find()
                        && reachable.add(candidate)) {
                    pending.add(candidate);
                }
            }
        }
        return Set.copyOf(reachable);
    }

    /**
     * Verifies the deleted compatibility store cannot return and domain/io sources
     * remain independent of Swing UI.
     *
     * @throws IOException if project sources cannot be scanned
     */
    @Test
    void legacyStoreIsAbsentAndLowerLayersDoNotImportUi() throws IOException {
        Path sourceRoot = Path.of("src", "com", "kor", "admiralty");

        assertAll(
                () -> assertFalse(Files.exists(sourceRoot.resolve("io/Datastore.java"))),
                () -> assertNoImport(sourceRoot.resolve("beans"), "com.kor.admiralty.ui"),
                () -> assertNoImport(sourceRoot.resolve("io"), "com.kor.admiralty.ui"),
                () -> assertNoImport(sourceRoot.resolve("beans"), "javax.swing"),
                () -> assertNoImport(sourceRoot.resolve("beans"), "java.awt"),
                () -> assertNoImport(sourceRoot.resolve("io"), "javax.swing"),
                () -> assertNoImport(sourceRoot.resolve("io"), "java.awt"));
    }

    /**
     * Verifies every project source reachable from the GameData Refresh root
     * remains independent of the UI package, Swing, and AWT.
     *
     * @throws IOException if project sources cannot be scanned
     */
    @Test
    void gameDataRefreshSourceClosureDoesNotReachUiSwingOrAwt() throws IOException {
        Path sourceRoot = Path.of("src");
        Path refreshRoot = sourceRoot.resolve("com/kor/admiralty/io/GameDataRefresh.java");
        Path uiRoot = sourceRoot.resolve("com/kor/admiralty/ui");
        Set<Path> reachableSources = reachableProjectSources(sourceRoot, refreshRoot);

        for (Path source : reachableSources) {
            assertFalse(source.startsWith(uiRoot), () -> "GameData Refresh source closure reaches " + source);
            assertNoImport(source, "com.kor.admiralty.ui");
            assertNoImport(source, "javax.swing");
            assertNoImport(source, "java.awt");
        }
    }

    /**
     * Verifies remote acquisition has one production owner after the obsolete
     * standalone downloader and executor operation are removed.
     *
     * @throws IOException if the production executor source cannot be read
     */
    @Test
    void obsoleteStandaloneGameDataDownloaderIsAbsent() throws IOException {
        Path workersRoot = Path.of("src", "com", "kor", "admiralty", "ui", "workers");
        String executorSource = Files.readString(workersRoot.resolve("SwingWorkerExecutor.java"));

        assertAll(
                () -> assertFalse(Files.exists(workersRoot.resolve("FileDownloader.java"))),
                () -> assertFalse(executorSource.contains("downloadFile(")));
    }

    /**
     * Verifies the production background adapter schedules the supplied GameData
     * Refresh without reconstructing another instance from directory state.
     *
     * @throws IOException if the production executor source cannot be read
     */
    @Test
    void productionExecutorSchedulesTheSuppliedGameDataRefresh() throws IOException {
        Path executor = Path.of(
                "src", "com", "kor", "admiralty", "ui", "workers", "SwingWorkerExecutor.java");
        String executorSource = Files.readString(executor);

        assertAll(
                () -> assertTrue(executorSource.contains("exec(new UpdateDataFiles(refresh));")),
                () -> assertFalse(executorSource.contains("new GameDataRefresh(")));
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
     * Verifies the Admiral workspace and every UI helper it reaches receive
     * GameData and Ship icon rendering without consulting the application holder.
     * Application-wide bootstrap, icon acquisition, and persistence remain outside
     * this boundary.
     *
     * @throws IOException if project sources cannot be scanned
     */
    @Test
    void admiralWorkspacePathDoesNotImportApp() throws IOException {
        Path sourceRoot = Path.of("src");
        Path uiRoot = sourceRoot.resolve(Path.of("com", "kor", "admiralty", "ui"));
        Path workspaceRoot = uiRoot.resolve("panels/AdmiralPanel.java");
        Set<Path> reachableSources = reachableProjectSources(sourceRoot, workspaceRoot);

        assertAll(
                () -> assertFalse(
                        reachableSources.contains(sourceRoot.resolve("com/kor/admiralty/App.java")),
                        () -> "Admiral workspace source closure reaches App.java"),
                () -> assertNoImport(uiRoot.resolve("panels"), "com.kor.admiralty.App"));
    }

    /**
     * Verifies production exposes one stable Admiral workspace root and neither
     * the host nor console can return to a legacy or transitional root.
     *
     * @throws IOException if project sources cannot be scanned
     */
    @Test
    void soleAdmiralPanelWorkspaceIsEnforced() throws IOException {
        Path uiRoot = Path.of("src", "com", "kor", "admiralty", "ui");
        Path panelsRoot = uiRoot.resolve("panels");
        List<Path> productionSources = javaSourcesUnder(uiRoot);
        // Strings.AdmiralPanel is the established resource namespace, not a
        // workspace implementation, so the sole-root count excludes that owner.
        List<Path> workspaceTypeSources = productionSources.stream()
                .filter(path -> !path.equals(uiRoot.resolve("resources/Strings.java")))
                .collect(Collectors.toList());
        String hostSource = Files.readString(uiRoot.resolve("AdmiralWorkspaceHost.java"));
        String consoleSource = Files.readString(uiRoot.resolve("AdmiraltyConsole.java"));
        long admiralPanelFiles = productionSources.stream()
                .filter(path -> path.getFileName().toString().equals("AdmiralPanel.java"))
                .count();
        long admiralPanelDeclarations = countSourcesDeclaringType(workspaceTypeSources, "AdmiralPanel");
        long admiralPanel2Declarations = countSourcesDeclaringType(productionSources, "AdmiralPanel2");
        long admiralUiDeclarations = countSourcesDeclaringType(productionSources, "AdmiralUI");
        int hostConstructions = hostSource.split(Pattern.quote("new AdmiralPanel("), -1).length - 1;
        int hostedTabs = hostSource.split(Pattern.quote("tabs.addTab("), -1).length - 1;
        Pattern hostTabInstaller = Pattern.compile(
                "\\btabs\\s*\\.\\s*(?:add|addTab|insertTab|setComponentAt)\\s*\\(");
        Pattern consoleTabInstaller = Pattern.compile(
                "\\btabAdmirals\\s*\\.\\s*(?:add|addTab|insertTab|setComponentAt)\\s*\\(");
        long hostTabInstallations = hostTabInstaller.matcher(hostSource).results().count();
        long consoleTabInstallations = consoleTabInstaller.matcher(consoleSource).results().count();

        assertAll(
                () -> assertFalse(Files.exists(uiRoot.resolve("AdmiralPanel.java"))),
                () -> assertTrue(Files.exists(panelsRoot.resolve("AdmiralPanel.java"))),
                () -> assertEquals(1, admiralPanelFiles),
                () -> assertEquals(1, admiralPanelDeclarations),
                () -> assertEquals(0, admiralPanel2Declarations),
                () -> assertEquals(0, admiralUiDeclarations),
                () -> assertTrue(hostSource.contains("import com.kor.admiralty.ui.panels.AdmiralPanel;")),
                () -> assertEquals(1, hostConstructions),
                () -> assertEquals(1, hostedTabs),
                () -> assertEquals(1, hostTabInstallations),
                () -> assertEquals(0, consoleTabInstallations),
                () -> assertTrue(hostSource.contains("AdmiralPanel workspace = createWorkspace(admiral);")),
                () -> assertTrue(hostSource.contains("tabs.addTab(admiral.getName(), workspace);")),
                () -> assertFalse(hostSource.contains("AdmiralPanel2")),
                () -> assertTrue(consoleSource.contains("new AdmiralWorkspaceHost(")),
                () -> assertNoImport(
                        uiRoot.resolve("AdmiraltyConsole.java"),
                        "com.kor.admiralty.ui.panels"),
                () -> assertFalse(consoleSource.contains("tabAdmirals.addTab(")),
                () -> assertFalse(consoleSource.contains("new AdmiralPanel(")),
                () -> assertFalse(consoleSource.contains("AdmiralPanel2")),
                () -> assertFalse(consoleSource.contains("AdmiralUI")));
    }

    /**
     * Verifies runtime Admiral types remain independent of the JAXB wire
     * representation owned by AdmiralsStore.
     *
     * @throws IOException if the runtime source files cannot be scanned
     */
    @Test
    void runtimeAdmiralTypesDoNotImportJaxb() throws IOException {
        Path beansRoot = Path.of("src", "com", "kor", "admiralty", "beans");

        assertAll(
                () -> assertNoImport(beansRoot.resolve("Admiral.java"), "javax.xml.bind"),
                () -> assertNoImport(beansRoot.resolve("Admirals.java"), "javax.xml.bind"));
    }

    /**
     * Verifies deployment dialog markup and messages remain owned by Swing rather
     * than the Admiral domain seam.
     *
     * @throws IOException if the Admiral source cannot be scanned
     */
    @Test
    void admiralContainsNoDeploymentPresentationText() throws IOException {
        Path admiralSource = Path.of("src", "com", "kor", "admiralty", "beans", "Admiral.java");
        String source = Files.readString(admiralSource);

        assertAll(
                () -> assertFalse(source.contains("<html>")),
                () -> assertFalse(source.contains("Active ship(s) assigned")),
                () -> assertFalse(source.contains("One-time ship(s) assigned")),
                () -> assertFalse(source.contains("These ships have already been assigned.")));
    }

    /**
     * Verifies JAXB remains an implementation detail of AdmiralsStore throughout
     * production sources.
     *
     * @throws IOException if project sources cannot be scanned
     */
    @Test
    void onlyAdmiralsStoreImportsJaxb() throws IOException {
        Path sourceRoot = Path.of("src", "com", "kor", "admiralty");
        Path admiralsStore = sourceRoot.resolve("io/AdmiralsStore.java");

        assertOnlyFileImports(sourceRoot, "javax.xml.bind", admiralsStore);
    }

    /**
     * Proves ordinary wildcard imports and fully qualified helper references
     * cannot hide dependencies from the module checks; prose is not an edge.
     *
     * @param sourceRoot isolated synthetic source tree
     * @throws IOException if the fixture cannot be written or scanned
     */
    @Test
    void sourceClosureFindsWildcardAndQualifiedHelpers(@TempDir Path sourceRoot) throws IOException {
        Path root = sourceRoot.resolve("example/module/Root.java");
        Path wildcardHelper = sourceRoot.resolve("example/elsewhere/WildcardHelper.java");
        Path qualifiedHelper = sourceRoot.resolve("example/other/QualifiedHelper.java");
        Path decoy = sourceRoot.resolve("example/elsewhere/Decoy.java");
        for (Path source : List.of(root, wildcardHelper, qualifiedHelper, decoy)) {
            Files.createDirectories(source.getParent());
        }
        Files.writeString(root, """
                package example.module;
                import example.elsewhere.*;
                class Root {
                    WildcardHelper first;
                    example.other.QualifiedHelper second;
                    // Decoy and example.elsewhere.Decoy are documentation only.
                    String label = "example.elsewhere.Decoy";
                }
                """);
        Files.writeString(wildcardHelper, "package example.elsewhere; public class WildcardHelper {}");
        Files.writeString(qualifiedHelper, "package example.other; public class QualifiedHelper {}");
        Files.writeString(decoy, "package example.elsewhere; public class Decoy {}");

        assertEquals(Set.of(root, wildcardHelper, qualifiedHelper), reachableProjectSources(sourceRoot, root));
    }

    /**
     * Rejects retired Ship Filter declarations anywhere in production, including
     * package-private compatibility types hidden in another source file.
     *
     * @throws IOException if production sources cannot be scanned
     */
    @Test
    void retiredShipFilterPanelsAndListModelsRemainAbsent() throws IOException {
        Path sourceRoot = Path.of("src", "com", "kor", "admiralty");
        // Strings.ShipSelectionPanel is a label namespace shared with the new
        // presentation, not a legacy panel or a forwarding implementation.
        List<Path> sources = javaSourcesUnder(sourceRoot);
        for (String retired : List.of(
                "ShipSelectionPanel", "ShipListPanel", "AbstractShipListModel",
                "ShipListModel", "RosterCardListModel", "ShipUsageListModel")) {
            List<Path> implementationSources = sources.stream()
                    .filter(path -> !retired.equals("ShipSelectionPanel")
                            || !path.equals(sourceRoot.resolve("ui/resources/Strings.java")))
                    .toList();
            assertEquals(0, countSourcesDeclaringType(implementationSources, retired),
                    () -> "Retired Ship Filter type returned: " + retired);
        }
    }

    /**
     * Keeps the headless projection and its helpers inside the presentation
     * module, with domain values as its only project dependencies. The source
     * closure follows new helpers without prescribing their private names.
     *
     * @throws IOException if production sources cannot be scanned
     */
    @Test
    void headlessShipFilterImplementationStaysInsideItsModule() throws IOException {
        Path sourceRoot = Path.of("src");
        Path projectRoot = sourceRoot.resolve("com/kor/admiralty");
        Path moduleRoot = projectRoot.resolve("ui/shipfilter");
        Set<Path> domainPackages = Set.of(projectRoot.resolve("beans"), projectRoot.resolve("enums"));
        for (String entryPoint : List.of("ShipFilter.java", "ShipFilters.java")) {
            for (Path source : reachableProjectSources(sourceRoot, moduleRoot.resolve(entryPoint), domainPackages)) {
                assertTrue(source.startsWith(moduleRoot)
                                || domainPackages.stream().anyMatch(source::startsWith),
                        () -> "Headless Ship Filter reaches presentation implementation outside its module: " + source);
                if (source.startsWith(moduleRoot)) {
                    assertNoImport(source, "javax.swing");
                    assertNoImport(source, "java.awt");
                }
            }
        }
    }

    /**
     * Confines Swing filtering helpers to the same module while preserving the
     * established domain, artwork, and Ship details dependencies. A new helper
     * outside that boundary fails regardless of its implementation name.
     *
     * @throws IOException if production sources cannot be scanned
     */
    @Test
    void swingShipFilterImplementationStaysInsideItsModule() throws IOException {
        Path sourceRoot = Path.of("src");
        Path projectRoot = sourceRoot.resolve("com/kor/admiralty");
        Path moduleRoot = projectRoot.resolve("ui/shipfilter");
        Set<Path> existingDependencies = Set.of(
                projectRoot.resolve("beans"), projectRoot.resolve("enums"),
                projectRoot.resolve("ui/resources/Strings.java"),
                projectRoot.resolve("ui/resources/Swing.java"),
                projectRoot.resolve("ui/resources/ShipIconFactory.java"),
                projectRoot.resolve("ui/renderers/RosterCardCellRenderer.java"),
                projectRoot.resolve("ui/renderers/ShipCellRenderer.java"),
                projectRoot.resolve("ui/renderers/StarshipTraitCellRenderer.java"),
                projectRoot.resolve("ui/renderers/UsageCountCellRenderer.java"),
                projectRoot.resolve("ui/components/JColumnList.java"),
                projectRoot.resolve("ui/components/JListComponentAdapter.java"),
                projectRoot.resolve("ui/ShipDetailsPanel.java"));
        for (String entryPoint : List.of("ShipFilterView.java", "ShipFilterViews.java")) {
            for (Path source : reachableProjectSources(sourceRoot, moduleRoot.resolve(entryPoint), existingDependencies)) {
                assertTrue(source.startsWith(moduleRoot)
                                || existingDependencies.stream().anyMatch(source::startsWith),
                        () -> "Swing Ship Filter reaches implementation outside its module: " + source);
            }
        }
    }

    /**
     * Allows only the agreed public entry points in the Ship Filter module.
     * Internal adapters, projections, and Swing machinery may be renamed or
     * replaced freely without becoming another supported caller seam.
     *
     * @throws IOException if module sources cannot be scanned
     */
    @Test
    void shipFilterModuleExposesOnlyItsNamedPublicSeams() throws IOException {
        Path moduleRoot = Path.of("src", "com", "kor", "admiralty", "ui", "shipfilter");
        Set<String> entryPoints = Set.of("ShipFilter", "ShipFilters", "ShipFilterView", "ShipFilterViews");
        Pattern publicType = Pattern.compile(
                "\\bpublic\\s+(?:(?:abstract|static|final|sealed|non-sealed)\\s+)*"
                        + "(?:class|interface|record|enum)\\s+(\\w+)");
        Set<String> exposedTypes = new HashSet<>();
        for (Path source : javaSourcesUnder(moduleRoot)) {
            String code = codeTokensOnly(Files.readString(source));
            var declarations = publicType.matcher(code);
            while (declarations.find()) {
                String type = declarations.group(1);
                assertTrue(entryPoints.contains(type), () -> "Unexpected public Ship Filter seam: " + source + " / " + type);
                exposedTypes.add(type);
            }
        }
        assertEquals(entryPoints, exposedTypes);
    }

    /**
     * Verifies the production Roster selection adapter delegates every modal
     * workflow through named Ship Filter paths and cannot return to either
     * retired selection panel.
     *
     * @throws IOException if the adapter source cannot be read
     */
    @Test
    void rosterSelectionAdapterDoesNotReferenceLegacyPanels() throws IOException {
        Path adapter = Path.of(
                "src", "com", "kor", "admiralty", "ui", "panels", "RosterSelectionDialog.java");
        String code = codeTokensOnly(Files.readString(adapter));

        assertAll(
                () -> assertFalse(Pattern.compile("\\bShipSelectionPanel\\b").matcher(code).find()),
                () -> assertFalse(Pattern.compile("\\bShipListPanel\\b").matcher(code).find()));
    }

    /**
     * Lexical regions relevant to excluding non-code text from simple source
     * dependency discovery.
     */
    private enum JavaSourceRegion {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER,
        TEXT_BLOCK
    }
}
