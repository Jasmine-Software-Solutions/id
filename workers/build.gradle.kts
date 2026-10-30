import kotlin.io.path.Path

plugins {
    kotlin("jvm")

    id("gg.jte.gradle") version "3.2.4"
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
    implementation(project(":server"))

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Gradle 9 no longer puts the platform launcher on the test runtime classpath.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("ch.qos.logback:logback-classic:1.5.38")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.30.0-alpha")
    implementation("com.google.code.gson:gson:2.13.2")

    implementation("org.jetbrains.exposed:exposed-core:0.40.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.40.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.40.1")
    implementation("com.zaxxer:HikariCP:4.0.3")

    implementation(platform("software.amazon.awssdk:bom:2.29.52"))
    implementation("software.amazon.awssdk:sqs")
    implementation("software.amazon.awssdk:sns")

    implementation("gg.jte:jte:3.2.4")
    implementation("gg.jte:jte-kotlin:3.2.4")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Test> {
    maxParallelForks = 1
}

tasks.shadowJar {
    isZip64 = true
    // Required for mergeServiceFiles() to see more than the first file at a
    // given path; otherwise gRPC's NameResolverProvider registration is lost.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}

kotlin {
    jvmToolchain(26)
    compilerOptions {
        javaParameters.set(true)
    }
}

jte {
    sourceDirectory.set(Path("src/main/resources/templates"))
    targetDirectory.set(Path("build/generated-sources/jte"))
    contentType.set(gg.jte.ContentType.Html)
    packageName.set("gg.jte.generated.precompiled")
    generate()
}
