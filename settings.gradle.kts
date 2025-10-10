rootProject.name = "ai-dev-kit"

pluginManagement {
    includeBuild("publishing")
}

include("eval")
include("examples")
include("tracing:tracing-anthropic")
include("tracing:tracing-core")
include("tracing:tracing-gemini")
include("tracing:tracing-ktor")
include("tracing:tracing-openai")
include("tracing:tracing-test-utils")
includeBuild("plugin")
