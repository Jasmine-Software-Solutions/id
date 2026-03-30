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

    api("org.slf4j:slf4j-simple:2.0.3")
    api("org.jetbrains.exposed:exposed-core:0.40.1")
    api("org.jetbrains.exposed:exposed-dao:0.40.1")
    api("org.jetbrains.exposed:exposed-jdbc:0.40.1")

    api("io.github.cdimascio:dotenv-kotlin:6.4.1")

    api("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

    api("com.mysql:mysql-connector-j:9.3.0")
    api("org.postgresql:postgresql:42.7.4")
    api("com.zaxxer:HikariCP:4.0.3")

    api("de.mkammerer:argon2-jvm:2.12")

    api("commons-codec:commons-codec:1.17.1")
    api("dev.turingcomplete:kotlin-onetimepassword:2.4.1")

    api("org.eclipse.angus:angus-mail:2.0.1")

    api("com.fasterxml.jackson.core:jackson-annotations:2.18.3")
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
