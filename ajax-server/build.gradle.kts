import kotlin.io.path.Path

plugins {
    kotlin("jvm")
    id("gg.jte.gradle") version "3.1.15"

    id("com.github.johnrengelman.shadow") version "8.0.0"
}

group = "com.jasminesoftwaresolutions"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()

    maven("https://maven.reposilite.com/snapshots")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")

    implementation(project(":server"))

    implementation("gg.jte:jte:3.1.16")
    implementation("gg.jte:jte-watcher:3.1.15")

    implementation("gg.jte:jte-kotlin:3.1.15")

    implementation("io.javalin:javalin:6.6.0")
    implementation("io.javalin:javalin-rendering:6.4.0")

    implementation("io.javalin.community.routing:routing-core:6.4.1-SNAPSHOT")
    implementation("io.javalin.community.routing:routing-annotated:6.4.1-SNAPSHOT")
    implementation("io.javalin.community.routing:routing-coroutines:6.4.1-SNAPSHOT")

    testImplementation("com.microsoft.playwright:playwright:1.51.0")
}

jte {
    sourceDirectory.set(Path("src/main/kotlin/jte"))
    generate()
}

tasks.test {
    useJUnitPlatform()

    environment("ENV_FILE_PATH", "test.env")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "app.ApplicationKt"
    }
}

tasks.withType<Test> {
    maxParallelForks = 1
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        javaParameters.set(true)
    }
}
