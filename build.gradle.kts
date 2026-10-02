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
    implementation("com.github.cryptomorin:XSeries:13.7.1")
    implementation("org.bstats:bstats-bukkit:3.2.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.yaml:snakeyaml:2.2")
    testImplementation("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
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
    // bStats lives in our own package so it never clashes with other plugins that ship it
    relocate("org.bstats", "ru.mysticchest.libs.bstats")
    minimize()
}

tasks.test { useJUnitPlatform() }

tasks.build { dependsOn(tasks.shadowJar) }
