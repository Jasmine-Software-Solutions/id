rootProject.name = "id"
include("domain")
include("interfaces")
include("server")

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}