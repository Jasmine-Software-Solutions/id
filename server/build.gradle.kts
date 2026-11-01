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
    implementation(project(":domain")) {
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }
    implementation(project(":interfaces"))
    implementation("com.jasminesoftwaresolutions:id-java-client:1.0.0-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Gradle 9 no longer puts the platform launcher on the test runtime classpath.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")

    implementation("ch.qos.logback:logback-classic:1.5.38")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.30.0-alpha")

    implementation("org.jetbrains.exposed:exposed-core:0.40.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.40.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.40.1")

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

    implementation("io.javalin:javalin:6.6.0")
    implementation("io.javalin:javalin-rendering:6.4.0")

    implementation("io.javalin.community.routing:routing-core:6.4.1-SNAPSHOT")
    implementation("io.javalin.community.routing:routing-annotated:6.4.1-SNAPSHOT")
    implementation("io.javalin.community.routing:routing-coroutines:6.4.1-SNAPSHOT")

    implementation("com.resend:resend-java:+")

    implementation(platform("software.amazon.awssdk:bom:2.29.52"))
    implementation("software.amazon.awssdk:sns")

    implementation("io.grpc:grpc-netty:1.75.0")

    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.30.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "app.application.ApplicationKt"
    }
}

tasks.shadowJar {
    isZip64 = true
    // Required for mergeServiceFiles() to see more than the first file at a
    // given path; otherwise gRPC's NameResolverProvider registration is lost.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
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
