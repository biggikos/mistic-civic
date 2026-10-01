plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
}

group = "ru.mysticchest"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.helpch.at/releases/")
    maven("https://jitpack.io")
}

dependencies {
    // Compile against an older API with Java 8 bytecode so one jar loads on 1.12.2 .. 1.21+.
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("com.github.cryptomorin:XSeries:9.10.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.yaml:snakeyaml:2.2")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(8)
}

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.cryptomorin.xseries", "ru.mysticchest.libs.xseries")
    minimize()
}

tasks.test { useJUnitPlatform() }

tasks.build { dependsOn(tasks.shadowJar) }
