plugins {
    kotlin("jvm") version "2.3.20" apply false
    id("maven-publish")
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "maven-publish")

        extensions.configure<JavaPluginExtension>("java") {
            withSourcesJar()
        }

        extensions.configure<PublishingExtension>("publishing") {
            publications {
                create<MavenPublication>("mavenJvm") {
                    from(components["java"])
                    artifactId = project.name
                }
            }

            repositories {
                maven {
                    name = "codeArtifact"
                    url = uri("https://jasmine-software-solutions-435238036697.d.codeartifact.us-east-1.amazonaws.com/maven/id/")
                    credentials {
                        username = "aws"
                        password = System.getenv("CODEARTIFACT_AUTH_TOKEN")
                    }
                }
            }
        }
    }
}