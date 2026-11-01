plugins {
    kotlin("jvm")

    id("com.gradleup.shadow") version "9.0.2"
}

group = "com.jasminesoftwaresolutions"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()

    maven("https://maven.reposilite.com/snapshots")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Gradle 9 no longer puts the platform launcher on the test runtime classpath.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    api("org.slf4j:slf4j-simple:2.0.3")

    api("com.mysql:mysql-connector-j:9.3.0")
    api("org.postgresql:postgresql:42.7.4")
    api("com.zaxxer:HikariCP:4.0.3")

    api("de.mkammerer:argon2-jvm:2.12")

    api("commons-codec:commons-codec:1.17.1")
    api("dev.turingcomplete:kotlin-onetimepassword:2.4.1")

    api("org.eclipse.angus:angus-mail:2.0.1")

    api("com.fasterxml.jackson.core:jackson-annotations:2.18.3")

    implementation("com.google.code.gson:gson:2.13.2")

    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-gson:0.13.0")

    implementation("io.javalin:javalin:6.6.0")
    implementation("io.javalin:javalin-rendering:6.4.0")

    implementation("io.javalin.community.routing:routing-core:6.4.1-SNAPSHOT")
    implementation("io.javalin.community.routing:routing-annotated:6.4.1-SNAPSHOT")
    implementation("io.javalin.community.routing:routing-coroutines:6.4.1-SNAPSHOT")

    api("com.jasminesoftwaresolutions:witchwood:1.0-SNAPSHOT")
}

tasks.test {
    useJUnitPlatform()
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
    jvmToolchain(26)
    compilerOptions {
        javaParameters.set(true)
    }
}
