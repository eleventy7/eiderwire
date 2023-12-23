pluginManagement {
    plugins {
    }
    resolutionStrategy {
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "eider-wire"
include("eider-tool")
include("eider-internals")
include("eider-processor")
include("eider-java-writer")
include("eider-test")
