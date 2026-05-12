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
    implementation(project(":domain"))
    implementation(project(":interfaces"))

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    implementation("org.slf4j:slf4j-simple:2.0.3")
    implementation("org.jetbrains.exposed:exposed-core:0.40.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.40.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.40.1")

    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-gson:0.13.0")

    implementation("com.mysql:mysql-connector-j:9.3.0")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:4.0.3")

    implementation("de.mkammerer:argon2-jvm:2.12")

    implementation("commons-codec:commons-codec:1.17.1")
    implementation("dev.turingcomplete:kotlin-onetimepassword:2.4.1")

    implementation("org.eclipse.angus:angus-mail:2.0.1")

    implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.3")

    implementation("com.google.code.gson:gson:2.13.2")

    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-gson:0.13.0")

    implementation("gg.jte:jte:3.2.4-jasmine.1-SNAPSHOT")
    implementation("gg.jte:jte-watcher:3.2.4-jasmine.1-SNAPSHOT")

    implementation("gg.jte:jte-kotlin:3.2.4-jasmine.1-SNAPSHOT")

    implementation("io.javalin:javalin:6.6.0")
    implementation("io.javalin:javalin-rendering:6.4.0")

    implementation("io.javalin.community.routing:routing-core:6.4.1-SNAPSHOT")
    implementation("io.javalin.community.routing:routing-annotated:6.4.1-SNAPSHOT")
    implementation("io.javalin.community.routing:routing-coroutines:6.4.1-SNAPSHOT")

    implementation("com.resend:resend-java:+")
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
        attributes["Main-Class"] = "app.application.ApplicationKt"
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
