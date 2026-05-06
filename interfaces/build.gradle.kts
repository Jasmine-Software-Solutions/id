import kotlin.io.path.Path

plugins {
    kotlin("jvm")
    id("gg.jte.gradle") version "3.2.4-jasmine.1-SNAPSHOT"

    id("com.github.johnrengelman.shadow") version "8.0.0"
}

group = "com.jasminesoftwaresolutions"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()

    maven("https://maven.reposilite.com/snapshots")

    maven {
        url = uri("https://jasmine-software-solutions-435238036697.d.codeartifact.us-east-1.amazonaws.com/maven/jte/")
        credentials {
            username = "aws"
            password = System.getenv("CODEARTIFACT_AUTH_TOKEN")
        }
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")

    implementation(project(":domain"))

    implementation("gg.jte:jte:3.2.4-jasmine.1-SNAPSHOT")
    implementation("gg.jte:jte-watcher:3.2.4-jasmine.1-SNAPSHOT")

    implementation("gg.jte:jte-kotlin:3.2.4-jasmine.1-SNAPSHOT")

    implementation("io.javalin:javalin:6.6.0")
    implementation("io.javalin:javalin-rendering:6.4.0")

    implementation("io.javalin.community.routing:routing-core:6.4.1-SNAPSHOT")
    implementation("io.javalin.community.routing:routing-annotated:6.4.1-SNAPSHOT")
    implementation("io.javalin.community.routing:routing-coroutines:6.4.1-SNAPSHOT")

    testImplementation("com.microsoft.playwright:playwright:1.51.0")

    implementation("com.google.code.gson:gson:2.13.2")
}

jte {
    sourceDirectory.set(Path("src/main/resources/templates"))
    targetDirectory.set(Path("build/generated-sources/jte"))
    contentType.set(gg.jte.ContentType.Html)
    packageName.set("gg.jte.generated.precompiled")

    generate()
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
    jvmToolchain(17)
    compilerOptions {
        javaParameters.set(true)
    }
}
