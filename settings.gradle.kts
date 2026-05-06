rootProject.name = "id"
include("domain")
include("interfaces")
include("server")

pluginManagement {
    repositories {
        maven {
            name = "codeArtifactPlugins"
            url = uri("https://jasmine-software-solutions-435238036697.d.codeartifact.us-east-1.amazonaws.com/maven/jte/")
            credentials {
                username = "aws"
                password = System.getenv("CODEARTIFACT_AUTH_TOKEN")
            }
        }

        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}
