rootProject.name = "ai-dev-kit"

include("eval")
include("examples")
include("plugin")
include("tracing:tracing-core")
include("tracing:tracing-anthropic")
include("tracing:tracing-gemini")
include("tracing:tracing-ktor")
include("tracing:tracing-openai")
include("tracing:tracing-test-utils")
includeBuild("plugin/gradle-tracing-plugin")
includeBuild("plugin/tracing-compiler-plugin")
