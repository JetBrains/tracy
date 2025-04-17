rootProject.name = "ai-dev-kit"

includeBuild("ai-dev-kit-trace/ai-dev-kit-trace-gradle")
includeBuild("ai-dev-kit-trace/ai-dev-kit-trace-plugin")
include("ai-dev-kit-core")
include("ai-dev-kit-example")
include("ai-dev-kit-test-base")
include("ai-dev-kit-tracking-providers")
include("ai-dev-kit-tracking-providers:ai-dev-kit-tracking-mlflow")
include("ai-dev-kit-trace")
include("ai-dev-kit-tracking-providers:ai-dev-kit-tracking-wandb")
