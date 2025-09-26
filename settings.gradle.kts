rootProject.name = "ai-dev-kit"

include("eval")
include("examples")
include("plugin")
include("tracing")
includeBuild("plugin/gradle-tracing-plugin")
includeBuild("plugin/tracing-compiler-plugin")
