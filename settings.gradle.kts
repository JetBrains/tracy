//pluginManagement {
//    repositories {
//        gradlePluginPortal()
//        mavenCentral()
//    }
//}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
//    versionCatalogs {
//        register("libs") {
//            from(files("gradle/libs.versions.toml"))
//        }
//    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "ai-dev-kit"

include("ai-dev-kit-core")
include("ai-dev-kit-tracking-providers")
include("ai-dev-kit-tracking-providers:ai-dev-kit-tracking-mlflow")
include("ai-dev-kit-example")
