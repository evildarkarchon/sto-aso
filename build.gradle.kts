import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.wrapper.Wrapper
import java.util.jar.Attributes
import java.util.jar.JarFile

plugins {
    java
    id("org.openrewrite.rewrite") version "7.39.0"
}

group = "Admiralty"
version = "1.0.5-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src"))
            include("com/**/*.java")
        }
        resources {
            setSrcDirs(listOf("src"))
            // The package tree owns application resources; ignored graph caches under src are development state.
            include("com/**")
            exclude("**/*.java", "**/graphify-out/**")
        }
    }
    test {
        java.setSrcDirs(listOf("test"))
        resources.setSrcDirs(listOf("test/resources"))
    }
}

dependencies {
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:2.3.3")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.9")
    implementation("org.apache.commons:commons-csv:1.3")
    implementation("org.swinglabs.swingx:swingx-all:1.6.4")
    implementation("com.github.rjeschke:txtmark:0.13")

    constraints {
        implementation("commons-codec:commons-codec:1.17.2")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")

    rewrite("org.openrewrite.recipe:rewrite-migrate-java:3.42.1")
}

rewrite {
    activeRecipe("org.openrewrite.java.migrate.UpgradeToJava25")
    setExportDatatables(true)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

tasks.jar {
    manifest {
        attributes[Attributes.Name.MAIN_CLASS.toString()] = "com.kor.admiralty.ui.AdmiraltyConsole"
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
    systemProperty("java.awt.headless", "true")
    workingDir = rootProject.projectDir
    classpath = sourceSets.test.get().runtimeClasspath
}

val visualBaselineDataDirectory = layout.buildDirectory.dir("visual-baseline-data")

tasks.register<JavaExec>("shipFilterVisualBaseline") {
    group = "verification"
    description = "Runs a Ship Filter visual-baseline or native interaction mode."
    dependsOn(tasks.testClasses)
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
    mainClass = "com.kor.admiralty.ui.ShipFilterVisualBaseline"
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("java.awt.headless", "false")
    systemProperty(
        "admiralty.visualBaselineDataDirectory",
        visualBaselineDataDirectory.get().asFile.absolutePath)
    workingDir = rootProject.projectDir
}

val verifyThinJar = tasks.register("verifyThinJar") {
    group = "verification"
    description = "Verifies the Admiralty JAR manifest, resources, and thin artifact boundary."
    dependsOn(tasks.jar)

    val archiveFile = tasks.jar.flatMap { it.archiveFile }
    val intendedResources = fileTree("src/com") {
        exclude("**/*.java", "**/graphify-out/**")
    }
    inputs.file(archiveFile)
    inputs.files(intendedResources)

    doLast {
        val expectedMainClass = "com.kor.admiralty.ui.AdmiraltyConsole"
        val expectedResources = intendedResources.files
            .map { it.relativeTo(file("src")).invariantSeparatorsPath }
            .toSet()

        JarFile(archiveFile.get().asFile).use { archive ->
            val mainClass = archive.manifest.mainAttributes.getValue(Attributes.Name.MAIN_CLASS)
            check(mainClass == expectedMainClass) {
                "Expected Main-Class $expectedMainClass but found ${mainClass ?: "no value"}."
            }

            val entries = archive.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .toSet()
            val packagedResources = entries
                .filter { it.startsWith("com/") && !it.endsWith(".class") }
                .toSet()
            val applicationClasses = entries
                .filter { it.endsWith(".class") }
                .toSet()

            check(packagedResources == expectedResources) {
                "Packaged resources differ from src/com: missing=${expectedResources - packagedResources}, " +
                        "unexpected=${packagedResources - expectedResources}."
            }
            check(entries.none { it.contains("graphify-out/") }) {
                "The JAR contains generated graph cache content."
            }
            check(entries.none { it.startsWith("data/") || it.startsWith("gamedata/") }) {
                "The JAR contains external GameData or test GameData fixtures."
            }
            check(entries.none { it.endsWith(".jar") }) {
                "The JAR contains nested dependency archives."
            }
            check(applicationClasses.all { it.startsWith("com/kor/admiralty/") }) {
                "The JAR contains classes outside the Admiralty application package."
            }
            val unexpectedPayload = entries - expectedResources - applicationClasses - "META-INF/MANIFEST.MF"
            check(unexpectedPayload.isEmpty()) {
                "The JAR contains unexpected payload files: $unexpectedPayload."
            }
        }

        check(!file("src/META-INF/MANIFEST.MF").exists()) {
            "The obsolete source manifest still exists; Gradle must own the JAR manifest directly."
        }
    }
}

val explodedBootstrapDirectory = layout.buildDirectory.dir("tmp/exploded-bootstrap")
val packagedBootstrapDirectory = layout.buildDirectory.dir("tmp/packaged-bootstrap")

val prepareExplodedBootstrap = tasks.register<Sync>("prepareExplodedBootstrap") {
    from("data") {
        include("*.csv", "hashes.md5")
    }
    into(explodedBootstrapDirectory)
    outputs.upToDateWhen { false }
}

val preparePackagedBootstrap = tasks.register<Sync>("preparePackagedBootstrap") {
    dependsOn(tasks.jar)
    from(tasks.jar.flatMap { it.archiveFile })
    from("data") {
        include("*.csv", "hashes.md5")
    }
    into(packagedBootstrapDirectory)
    outputs.upToDateWhen { false }
}

val verifyExplodedBootstrap = tasks.register<JavaExec>("verifyExplodedBootstrap") {
    group = "verification"
    description = "Verifies exploded Gradle classes retain the working-directory GameData fallback."
    dependsOn(prepareExplodedBootstrap, tasks.testClasses)
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
    mainClass = "com.kor.admiralty.ui.BuildArtifactBootstrapProbe"
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("java.awt.headless", "true")
    workingDir = explodedBootstrapDirectory.get().asFile
    args(
        sourceSets.main.get().output.classesDirs.singleFile.absolutePath,
        explodedBootstrapDirectory.get().asFile.absolutePath)
}

val verifyPackagedBootstrap = tasks.register<JavaExec>("verifyPackagedBootstrap") {
    group = "verification"
    description = "Verifies the packaged JAR resolves executable-adjacent external GameData."
    dependsOn(preparePackagedBootstrap, tasks.testClasses)
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
    mainClass = "com.kor.admiralty.ui.BuildArtifactBootstrapProbe"
    systemProperty("java.awt.headless", "true")
    workingDir = rootProject.projectDir

    doFirst {
        val installationDirectory = packagedBootstrapDirectory.get().asFile
        val packagedJar = installationDirectory.resolve(tasks.jar.get().archiveFileName.get())
        // Dependencies stay external: the probe classpath supplies them without changing the thin JAR.
        classpath = files(sourceSets.test.get().output, packagedJar, configurations.runtimeClasspath)
        setArgs(listOf(installationDirectory.absolutePath, installationDirectory.absolutePath))
    }
}

tasks.check {
    dependsOn(verifyThinJar, verifyExplodedBootstrap, verifyPackagedBootstrap)
}

tasks.wrapper {
    gradleVersion = "9.7.1"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a"
}
