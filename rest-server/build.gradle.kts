plugins {
    kotlin("jvm")

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

    implementation("io.javalin:javalin:6.6.0")

    implementation("io.javalin.community.routing:routing-core:6.4.1-SNAPSHOT")
    implementation("io.javalin.community.routing:routing-annotated:6.4.1-SNAPSHOT")
    implementation("io.javalin.community.routing:routing-coroutines:6.4.1-SNAPSHOT")
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
