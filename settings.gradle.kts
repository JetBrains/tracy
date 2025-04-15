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
include("ai-dev-kit-eval")
include("ai-dev-kit-eval:ai-dev-kit-eval-base")
include("ai-dev-kit-eval:ai-dev-kit-eval-mlflow")
include("ai-dev-kit-example")
include("ai-dev-kit-eval:ai-dev-kit-eval-wandb")
findProject(":ai-dev-kit-eval:ai-dev-kit-eval-wandb")?.name = "ai-dev-kit-eval-wandb"
