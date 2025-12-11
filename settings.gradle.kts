rootProject.name = "tracy"

pluginManagement {
    includeBuild("publishing")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("docs")
include("eval")
include("examples")
include("tracing:anthropic")
include("tracing:core")
include("tracing:gemini")
include("tracing:ktor")
include("tracing:openai")
include("tracing:test-utils")
includeBuild("plugin")
