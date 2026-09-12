import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.wrapper.Wrapper

plugins {
    java
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
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
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

tasks.wrapper {
    gradleVersion = "9.7.1"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a"
}
