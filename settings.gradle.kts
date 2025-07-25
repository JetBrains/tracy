rootProject.name = "ai-dev-kit"

include("ai-dev-kit-eval")
include("examples")
include("ai-dev-kit-plugin")
include("ai-dev-kit-tracing")
include("ai-dev-kit-tracking-providers")
include("ai-dev-kit-tracking-providers:ai-dev-kit-tracking-langfuse")
include("ai-dev-kit-tracking-providers:ai-dev-kit-tracking-mlflow")
include("ai-dev-kit-tracking-providers:ai-dev-kit-tracking-wandb")
includeBuild("ai-dev-kit-plugin/ai-dev-kit-trace-gradle")
includeBuild("ai-dev-kit-plugin/ai-dev-kit-trace-plugin")
