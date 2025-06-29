import kotlin.io.path.Path

plugins {
    kotlin("jvm") version "1.9.23"
    id("gg.jte.gradle") version "3.1.15"

    id("com.github.johnrengelman.shadow") version "8.0.0"
}

tasks.register<Copy>("copyResourcesToKotlinMain") {
    group = "build"
    description = "Copies resources to /build/classes/kotlin/main"

    val sourceDir = file("src/main/resources") // Update this path if your resources are located elsewhere
    val targetDir = file("build/classes/kotlin/main")

    from(sourceDir)
    into(targetDir)
    include("**/*") // Adjust as needed to specify which files to include

    doLast {
        println("Resources copied to $targetDir")
    }
}

tasks.register<Copy>("copyResourcesToKotlinTest") {
    group = "build"
    description = "Copies resources to /build/classes/kotlin/test"

    val sourceDir = file("src/main/resources")
    val targetDir = file("build/classes/kotlin/test")

    from(sourceDir)
    into(targetDir)
    include("**/*")

    doLast {
        println("Resources copied to $targetDir")
    }
}

tasks.build { dependsOn("copyResourcesToKotlinMain") }
tasks.test {
    dependsOn("copyResourcesToKotlinMain")
    dependsOn("copyResourcesToKotlinTest")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn("copyResourcesToKotlinMain")
}

tasks.shadowJar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn("copyResourcesToKotlinMain")
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

    implementation("gg.jte:jte:3.1.16")
    implementation("gg.jte:jte-watcher:3.1.15")

    implementation("gg.jte:jte-kotlin:3.1.15")

    implementation("io.javalin:javalin:6.6.0")
    implementation("io.javalin:javalin-rendering:6.4.0")

    implementation("org.slf4j:slf4j-simple:2.0.3")
    implementation("org.jetbrains.exposed:exposed-core:0.40.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.40.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.40.1")

    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

    implementation("com.mysql:mysql-connector-j:9.3.0")
    implementation("com.zaxxer:HikariCP:4.0.3")

    implementation("io.javalin.community.routing:routing-core:6.4.1-SNAPSHOT")
    implementation("io.javalin.community.routing:routing-annotated:6.4.1-SNAPSHOT")
    implementation("io.javalin.community.routing:routing-coroutines:6.4.1-SNAPSHOT")
    implementation("de.mkammerer:argon2-jvm:2.12")

    implementation("commons-codec:commons-codec:1.17.1")
    implementation("dev.turingcomplete:kotlin-onetimepassword:2.4.1")

    implementation("org.eclipse.angus:angus-mail:2.0.1")

    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")
    testImplementation("com.microsoft.playwright:playwright:1.51.0")

    implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.3")
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