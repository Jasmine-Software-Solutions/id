import kotlin.io.path.Path

plugins {
    kotlin("jvm")

    id("com.gradleup.shadow") version "9.0.2"
}

group = "com.jasminesoftwaresolutions"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://maven.reposilite.com/snapshots")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")

    implementation(project(":domain"))

    implementation("io.javalin:javalin:6.6.0")
    implementation("io.javalin:javalin-rendering:6.4.0")

    implementation("io.javalin.community.routing:routing-core:6.4.1-SNAPSHOT")
    implementation("io.javalin.community.routing:routing-annotated:6.4.1-SNAPSHOT")
    implementation("io.javalin.community.routing:routing-coroutines:6.4.1-SNAPSHOT")

    testImplementation("com.microsoft.playwright:playwright:1.51.0")

    implementation("com.google.code.gson:gson:2.13.2")

    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.30.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.jasminesoftwaresolutions.id.interfaces.user.ApplicationKt"
    }
}

tasks.withType<Test> {
    maxParallelForks = 1
}

kotlin {
    jvmToolchain(26)
    compilerOptions {
        javaParameters.set(true)
    }
}
