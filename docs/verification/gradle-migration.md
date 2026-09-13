# Gradle migration semantic parity

This record verifies that Gradle can replace Maven for the observable ASO build
and application contracts. It does not require byte-identical archives: archive
timestamps, entry ordering, build-tool metadata, Maven's accidental inclusion of
generated graph caches, and the intentional Gradle `Main-Class` correction are not
parity failures.

## Verification context

- Date: September 12, 2026 (`America/Los_Angeles`, UTC-07:00)
- OS: Windows 11 10.0 amd64
- Branch: `improve-codebase-architecture`
- Verified migration revision: `c3a40586bf9028d7da5ae2bc147499b4b2edf95a`
- Upstream merge base: `ac217efbe2d5f336d06a1369bc2294292f106bd2`
- The tracked worktree was clean before evidence collection. This record, its
  resolved local ticket, and the test-fixture LF rule described below are the only
  tracked changes made by this verification.

The unqualified `java --version` resolved through Oracle's `javapath` shim to
Oracle Java 26.0.2. That executable was not used by either build. `JAVA_HOME`,
Maven, and the Gradle Wrapper all selected the installed Temurin JDK 25:

| Command | Observed result |
| --- | --- |
| `& "$env:JAVA_HOME\bin\java.exe" --version` | Temurin OpenJDK 25.0.4.1 LTS |
| `mvn --version` | Apache Maven 3.9.16, running on Java 25.0.4.1 from `C:\Program Files\jdk` |
| `.\gradlew.bat --version` | Gradle 9.7.1, launcher and daemon on Temurin 25.0.4.1 |

## Lifecycle comparison

The reference and replacement lifecycles were run in the required order:

| Order | Exact command | Outcome |
| --- | --- | --- |
| 1 | `mvn clean package` | `BUILD SUCCESS`; 295 tests, 0 failures, 0 errors, 0 skipped; thin Maven JAR created in `target/` |
| 2 | `.\gradlew.bat clean build --console=plain` | `BUILD SUCCESSFUL`; 12 tasks executed, including all tests and the thin-JAR and bootstrap verification tasks |

Summing `build/test-results/test/TEST-*.xml` immediately after the Gradle build
confirmed 295 tests, 0 failures, 0 errors, and 0 skipped. Expected warning and
error logs emitted by failure-path tests did not fail either lifecycle.

The first restricted-process attempts to download Maven/Gradle artifacts were
denied network access. The same commands were repeated unchanged with normal
public repository access; the successful results above are the comparison runs.

## Dependency and XML contracts

The comparison used:

```powershell
mvn dependency:list "-DexcludeTransitive=true" "-DincludeScope=test"
.\gradlew.bat dependencies --configuration runtimeClasspath --console=plain
.\gradlew.bat dependencies --configuration testRuntimeClasspath --console=plain
.\gradlew.bat dependencyInsight --dependency commons-codec `
  --configuration runtimeClasspath --console=plain
.\gradlew.bat dependencyInsight --dependency rewrite-migrate-java `
  --configuration rewrite --console=plain
```

The first-order production dependencies match exactly:

| Coordinate | Maven | Gradle |
| --- | --- | --- |
| `jakarta.xml.bind:jakarta.xml.bind-api` | 2.3.3 | 2.3.3 |
| `org.glassfish.jaxb:jaxb-runtime` | 2.3.9 | 2.3.9 |
| `org.apache.commons:commons-csv` | 1.3 | 1.3 |
| `org.swinglabs.swingx:swingx-all` | 1.6.4 | 1.6.4 |
| `com.github.rjeschke:txtmark` | 0.13 | 0.13 |
| `org.junit.jupiter:junit-jupiter` (test) | 5.12.2 | 5.12.2 |

Gradle explicitly adds `org.junit.platform:junit-platform-launcher:1.12.2` at test
runtime so its launcher matches JUnit Jupiter 5.12.2. This is test infrastructure,
not an application runtime dependency. Maven dependency management and the Gradle
constraint both retain `commons-codec:commons-codec:1.17.2`; Gradle
`dependencyInsight` found no selected Commons Codec runtime dependency, confirming
that the constraint did not promote it to a direct dependency.

The build-tool integration necessarily differs: Maven used
`rewrite-maven-plugin:6.46.1`, while Gradle uses the resolution-tested
`org.openrewrite.rewrite` plugin 7.39.0. The behavior-defining recipe library
remains `org.openrewrite.recipe:rewrite-migrate-java:3.42.1`.

Despite its Jakarta coordinate, API 2.3.3 contains
`javax/xml/bind/JAXBContext.class`, and production imports remain under
`javax.xml.bind`. The following unchanged compatibility seams passed under Gradle:

```powershell
.\gradlew.bat test `
  --tests "com.kor.admiralty.io.AdmiralsXmlCompatibilityTest" `
  --tests "com.kor.admiralty.io.AdmiralsStoreTest" `
  --console=plain
```

They load historical Admirals XML, round-trip it through JAXB, and preserve the
legacy namespace-free element and map shape.

## Semantic output inventories

Fresh `target/` and `build/` trees were compared as sorted relative-path sets with
this exact PowerShell:

```powershell
function Relative-Files([string]$root, [string]$filter = '*') {
    $base = (Resolve-Path $root).Path
    Get-ChildItem $root -Recurse -File -Filter $filter |
        ForEach-Object {
            [IO.Path]::GetRelativePath($base, $_.FullName).Replace('\', '/')
        } |
        Sort-Object
}

$mavenMain = Relative-Files 'target/classes' '*.class'
$gradleMain = Relative-Files 'build/classes/java/main' '*.class'
$mavenTest = Relative-Files 'target/test-classes' '*.class'
$gradleTest = Relative-Files 'build/classes/java/test' '*.class'
Compare-Object $mavenMain $gradleMain
Compare-Object $mavenTest $gradleTest

$mavenResources = Relative-Files 'target/classes' |
    Where-Object { $_ -notlike '*.class' }
$gradleResources = Relative-Files 'build/resources/main'
Compare-Object $mavenResources $gradleResources

$mavenFixtures = Relative-Files 'target/test-classes' |
    Where-Object { $_ -notlike '*.class' }
$gradleFixtures = Relative-Files 'build/resources/test'
Compare-Object $mavenFixtures $gradleFixtures
```

| Inventory | Maven | Gradle | Difference |
| --- | ---: | ---: | --- |
| Production `.class` files | 259 | 259 | None |
| Test `.class` files | 80 | 80 | None |
| Main resources | 521 | 206 | 315 Maven-only files, all generated `graphify-out` cache content |
| Test fixtures | 7 | 7 | None |

All 206 intended non-Java resources below `src/com/**` are present in Gradle's
processed resources. There are no Gradle-only resources and no Maven-only
non-Graphify application resources. The 7 test fixtures are the 2 Admirals XML
files and 5 GameData CSV files. Excluding Maven's accidental graph-cache payload is
an intentional correction to the resource boundary, not missing application data.

### Fresh-checkout fixture correction

An additional full build from a source-only Windows checkout initially exposed a
pre-existing fixture portability defect. With `core.autocrlf=true`, the five
`test/resources/gamedata/*.csv` files were checked out as CRLF because only
production GameData had an LF rule. Their hand-checked test digests describe LF
bytes, so the same focused test failed under both Gradle and Maven with
`expected: INSTALLATION but was: VERIFICATION`.

The correction adds `/test/resources/gamedata/*.csv text eol=lf` to
`.gitattributes`. A new source-only snapshot made with the proposed worktree
attributes restored all five expected MD5 values, and
`.\gradlew.bat clean build --console=plain` then completed all 12 lifecycle and
verification tasks with 295 passing tests. This change stabilizes existing fixture
semantics; it does not create a Gradle-specific behavior.

## Thin JAR and external GameData

The fresh JARs were inspected with this PowerShell; Gradle's built-in
`verifyThinJar` task independently checks the same artifact boundary:

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem

foreach ($jarPath in @(
    'target/Admiralty-1.0.5-SNAPSHOT.jar',
    'build/libs/Admiralty-1.0.5-SNAPSHOT.jar'
)) {
    $zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path $jarPath))
    try {
        $entries = @($zip.Entries | Where-Object { $_.Name -ne '' })
        $manifestEntry = $zip.GetEntry('META-INF/MANIFEST.MF')
        $reader = [IO.StreamReader]::new($manifestEntry.Open())
        try {
            $manifest = $reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }
        [pscustomobject]@{
            Path = (Resolve-Path $jarPath).Path
            Files = $entries.Count
            Classes = @($entries | Where-Object FullName -like '*.class').Count
            NestedJars = @($entries | Where-Object FullName -like '*.jar').Count
            GraphCache = @($entries | Where-Object FullName -like '*graphify-out/*').Count
            GameData = @($entries | Where-Object {
                $_.FullName -like 'data/*' -or $_.FullName -like 'gamedata/*'
            }).Count
            MainClass = [regex]::Match(
                $manifest,
                '(?m)^Main-Class:\s*(.+)\r?$').Groups[1].Value
        }
    } finally {
        $zip.Dispose()
    }
}
```

| Property | Maven JAR | Gradle JAR |
| --- | ---: | ---: |
| File entries | 783 | 466 |
| Admiralty classes | 259 | 259 |
| Intended application resources | 206 | 206 |
| Generated Graphify files | 315 | 0 |
| Nested JARs | 0 | 0 |
| `data/` or `gamedata/` content | 0 | 0 |
| `Main-Class` | absent | `com.kor.admiralty.ui.AdmiraltyConsole` |

The Maven entry total also includes its manifest and two Maven metadata files.
The Gradle JAR contains only its manifest, Admiralty classes, and intended
resources; every class is below `com/kor/admiralty/`. Runtime dependencies,
GameData, test fixtures, and graph caches remain external.

The `Main-Class` entry is deliberately recorded separately from parity. Maven
copied the source manifest to its classes output but replaced it while packaging,
so its final JAR omitted the declared entry point. Gradle corrects that packaging
mismatch without creating a fat JAR or standalone distribution.

The clean Gradle build executed both bootstrap probes:

- `verifyExplodedBootstrap` ran from an isolated working directory containing
  GameData and confirmed exploded classes fall back to working-directory GameData.
- `verifyPackagedBootstrap` placed GameData beside the thin JAR and confirmed the
  executable directory wins even though the child JVM's working directory was the
  repository. Runtime dependencies were supplied externally on the probe
  classpath.

## Focused runtime and test seams

These exact Gradle filters all completed successfully:

```powershell
.\gradlew.bat test --tests "com.kor.admiralty.io.GameDataTest" --console=plain
.\gradlew.bat test `
  --tests "com.kor.admiralty.BuildHarnessTest.testsRunHeadlessly" `
  --console=plain
.\gradlew.bat test `
  --tests "com.kor.admiralty.ui.shipfilter.ShipFilterTypeSafetyTest" `
  --console=plain
.\gradlew.bat test `
  --tests "com.kor.admiralty.beans.SolverTest.largeRosterRetainsTopTenWithinSmallHeap" `
  --console=plain
.\gradlew.bat test `
  --tests "com.kor.admiralty.ui.AssignmentPanelTest" `
  --tests "com.kor.admiralty.ui.ShipUsagePanelTest" `
  --console=plain
```

Together with the XML command above, these prove:

- class and method filtering use native Gradle `--tests` syntax;
- automated Swing tests receive headless AWT;
- `ToolProvider.getSystemJavaCompiler()` is available from a full JDK and its
  dynamically compiled consumers receive the test runtime classpath;
- the real Solver completes in a child JVM with `-Xmx64m` and the inherited test
  classpath;
- classpath GameData/XML fixtures and project-relative filesystem GameData both
  resolve under the configured project working directory.

## Wrapper from a clean checkout

The repository at the verified revision was cloned locally with `--no-hardlinks`
into an ignored verification directory. Before and after the probe,
`git status --porcelain=v1 --untracked-files=all` was empty. A new, isolated
`GRADLE_USER_HOME` forced a cold distribution download:

```powershell
$sourceRepo = (git rev-parse --show-toplevel).Trim()
$clone = Join-Path $sourceRepo 'build/wrapper-git-clone'
$freshGradleHome = Join-Path $sourceRepo 'build/wrapper-git-clone-gradle-home'
git clone --no-hardlinks --branch improve-codebase-architecture $sourceRepo $clone

$previousGradleHome = $env:GRADLE_USER_HOME
Push-Location $clone
try {
    git status --porcelain=v1 --untracked-files=all
    $env:GRADLE_USER_HOME = $freshGradleHome
    .\gradlew.bat --version
    .\gradlew.bat -q javaToolchains
    git status --porcelain=v1 --untracked-files=all
} finally {
    $env:GRADLE_USER_HOME = $previousGradleHome
    Pop-Location
}
```

The Wrapper downloaded `gradle-9.7.1-bin.zip` to 100%, started Gradle 9.7.1, and
therefore accepted the pinned SHA-256
`acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a`.
`javaToolchains` reported `Auto-download: Disabled` and detected installed Temurin
JDK 25 instances; no JDK was provisioned.

## OpenRewrite dry run

The safe preview command was executed; the applying task was not:

```powershell
$before = git status --short
.\gradlew.bat rewriteDryRun --console=plain
$after = git status --short
.\gradlew.bat dependencyInsight `
  --dependency rewrite-migrate-java --configuration rewrite --console=plain
.\gradlew.bat tasks --all --console=plain
```

The dry run activated `org.openrewrite.java.migrate.UpgradeToJava25`, reported
prospective changes in 8 files, produced a 20,601-byte
`build/reports/rewrite/rewrite.patch` and a datatable file, and left tracked status
unchanged. Dependency insight resolved recipe library 3.42.1 exactly. Task listing
showed both `rewriteDryRun` and the intentionally unexecuted, source-mutating
`rewriteRun`. Resolution used only the public plugin portal and Maven Central; no
credentials were configured.

## Visible Swing verification

The dedicated non-headless Java 25 task was run in the documented Windows desktop
conditions at 100% display scaling. The existing Maven output was compared with
this hash inventory before and after both runs:

```powershell
function Tree-Hashes([string]$root) {
    $base = (Resolve-Path $root).Path
    Get-ChildItem $root -Recurse -File |
        ForEach-Object {
            $relative = [IO.Path]::GetRelativePath($base, $_.FullName).Replace('\', '/')
            "$relative|$((Get-FileHash -Algorithm SHA256 $_.FullName).Hash)"
        } |
        Sort-Object
}

$targetBefore = Tree-Hashes 'target'
.\gradlew.bat shipFilterVisualBaseline `
  --args="game-data-traits build/issue-06-game-data-traits.png" `
  --console=plain
.\gradlew.bat shipFilterVisualBaseline `
  --args="interaction-smoke" `
  --console=plain
$targetAfter = Tree-Hashes 'target'
Compare-Object $targetBefore $targetAfter
Get-FileHash -Algorithm SHA256 `
  'build/issue-06-game-data-traits.png', `
  'docs/visual-baselines/ship-filter-after-issue-45/game-data-traits.png'
```

The image-producing mode exited successfully and created a 640 x 480 PNG. Its
SHA-256 exactly matched the checked-in GameData Traits baseline:

```text
B972A3DB676F4B0E9FE1642B2EF97EE6786F3F23DF2391F751CB7DC371717903
```

The interaction smoke exited successfully after reporting passes for all 9
reusable/One-Time/Roster-card accept, cancel, and close cases, the usage controls,
the passive Roster behavior, and GameData Traits behavior. This is programmatic
native-window verification with dispatched Swing events, not physical input or a
claim of human manual testing. A before/after SHA-256 inventory of the existing
Maven `target/` tree had zero differences; harness scratch state and the caller-
selected PNG remained under `build/`.

## Evidence hygiene and conclusion

Generated JARs, compiled classes, Maven/Gradle output trees, dependency caches,
test reports, Rewrite patch/datatables, clean-checkout copies, visual scratch
state, and the generated comparison PNG remain ignored and are not evidence to
commit. Only this Markdown record, its resolved local ticket, and the LF fixture
rule are tracked.

The Maven and Gradle builds agree on dependencies, production and test classes,
intended resources, test fixtures, headless/compiler/subprocess behavior, JAXB XML,
and external GameData resolution. The only artifact differences are the intended
Graphify exclusion, normal build-tool metadata, and the separately accepted
`Main-Class` correction. Gradle 9.7.1 can therefore replace Maven without changing
the observed application contract.
