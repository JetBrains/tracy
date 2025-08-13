## 🛠️ General Information
The AI Development Kit is a toolkit
designed to streamline and speed up the development of AI-powered features at JetBrains. 
It tackles critical pain points across both research and product development workflows —
especially within the Kotlin and IntelliJ ecosystem.

## 💡 Motivation
* **Slow Prototyping**: Lack of internal APIs for experimentation delays research and prototyping.
* **Quality Gaps**: No consistent or systematic evaluation pipeline leads to risk of low-quality results.
* **Fragmented Tooling**: There’s no unified, Kotlin-native tooling for tracing, evaluation, and collaboration like what Python has.

## 🎯 Goals of the AI Development Kit
* **Unify Evaluation Practice**  
  Establish a consistent, test-driven, and criteria-based evaluation process across all development stages from early research to production deployment.
* **Bridge Python and JVM Tooling**  
  Integrate powerful Python-based evaluation tools into the Kotlin ecosystem.
* **Promote Collaboration**  
   Enables seamless collaboration between developers, QA engineers,
  and researchers through shared datasets, trace tracking,
  and unified tooling.
* **Support Agentic AI Development**  
   Provides robust infrastructure for agent-based workflows, including prompt engineering, traceable evaluations, and role-specific testing support.

## ⭐ Key features
* 🔍 Kotlin-native tracing via `@KotlinFlowTrace` and compiler plugin.
* 🔌 Integration with `Langfuse` and `Weights & Biases`.
* 📊 Evaluation framework with test cases and evaluation criteria.
* 🤖 Internal `OpenAI` compatible gateway with `LiteLLM` support. For a more detailed description, refer to the [article](https://youtrack.jetbrains.com/articles/JBAI-A-659/LiteLLM-Internal-LLM-Gateway-for-Research-and-Experimentation)

## 📚 How to use Tracing?

You can find the latest versions of `ai-dev-kit` [here](https://jetbrains.team/p/ai-development-kit/packages/maven/ai-development-kit).

#### 1. Add Maven Repository
In your `build.gradle.kts`, add the following Maven repository:
```kotlin
maven {
    url = uri("https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit")
}
```

#### 2. Set Up `libs.versions.toml`
Configure your libs.versions.toml file with the appropriate versions:
```toml
[versions]
ai-dev-kit-plugin = "VERSION"
ai-dev-kit = "VERSION"

[libraries]
ai-dev-kit-tracing = { module = "com.jetbrains:ai-dev-kit-tracing", version.ref = "ai-dev-kit" }

# You can choose from multiple tracing providers:
# - Langfuse (used in the example below)
# - Weights & Biases (W&B)
ai-dev-kit-tracking-langfuse = { module = "com.jetbrains:ai-dev-kit-tracking-langfuse", version.ref = "ai-dev-kit" }
# ai-dev-kit-tracking-wandb = { module = "com.jetbrains:ai-dev-kit-tracking-wandb", version.ref = "ai-dev-kit" }

[plugins]
ai-dev-kit = { id = "ai.dev.kit.trace", version.ref = "ai-dev-kit-plugin" }
```
Replace `VERSION` with the latest version from the JetBrains repository.

#### 3. Add Dependencies
In your module's `build.gradle.kts`, add the required dependencies:

```kotlin
dependencies {
    implementation(libs.ai.dev.kit.tracing)
    implementation(libs.ai.dev.kit.tracking.langfuse)
}
```

#### 4. Enable Tracing with `@KotlinFlowTrace`

* Make sure to apply the `ai-dev-kit` plugin in your `build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.ai.dev.kit)
}
```
* Set up tracing using a tracking provider.
  The example below uses Langfuse,
  but you can replace it with other supported platforms
  (Weights & Biases Weave) by making the corresponding changes in your setup:
```kotlin
// You can pass keys explicitly to config or leave them null to load from environment variables.
val langfuseConfig = LangfuseConfig()
TracingManager.setup(langfuseConfig)
// Your code with tracing
TracingManager.flushTraces()
```
⚠️ Important:
1. You **must** set up tracing by calling `TracingManager.setup` before using any annotated methods. If tracing is not initialized, the tracking provider will not be defined, and traces will not be recorded.
2. Ensure that you call `TracingManager.flushTraces()` after all tracing operations to flush any pending traces. Without this, traces may not be exported if the application terminates too quickly.


* Annotate traced function with `@KotlinFlowTrace`
* Use `addTagsToCurrentTrace` with a list of string tags as a parameter inside the annotated function to add tags to the current trace.     
```kotlin
    @KotlinFlowTrace
    fun f(/*parameters*/): /*return value*/{
        // function logic
        val tags = listOf<String>("tag1", "tag2", "tag3")
        addTagsToCurrentTrace(tags)
        // function logic
    }
```

#### 5. Specify the Project (Experiment) and Session (Run) to upload the traces to

To group several traces into [sessions on Langfuse](https://langfuse.com/docs/tracing-features/sessions), wrap your code
with `withSessionId` (or its non-suspend version, `withSessionIdBlocking`):

```kotlin
withSessionId("my-session-name") {
    // your traced code
}
```

If a session with this name doesn't exist yet, it'll be created automatically.
The Langfuse project is determined by your API keys.

With W&B Weave, there is no such thing as sessions or runs, but you can group your traces together into a project by
wrapping your code in `withProjectId` (or its non-suspend version, `withProjectIdBlocking`).

```kotlin
withProjectId("my-project-name") {
    // your traced code
}
```

If a project with this name does not exist yet, it will be created automatically.

```kotlin
val experimentId = TODO("Implement a nice handle for requesting an experiment ID from the server")
val runId = TODO("Implement a nice handle for getting a run ID from the server")
withProjectIdBlocking(experimentId) {
    withSessionIdBlocking(runId) {
        // your traced code
    }
}
```

⚠️ Beware that explicitly spawning a new `thread { ... }` or `runBlocking { ... }` will break the automagical
propagation of session and project
IDs ([JBAI-14126](https://youtrack.jetbrains.com/issue/JBAI-14126/Tracing-does-not-work-out-of-the-box-with-multi-threaded-code))

## 📚 How to use Evaluation?

The `ai-dev-kit-eval` module provides a lightweight, extensible set of APIs for evaluating AI features and models.
It helps you define test cases, run generators, compute scores, and optionally log results to external tracking systems.

### ⭐ Key Features

- Test APIs for AI evaluation
  - Define inputs, expected ground truth, and produce outputs via a Generator
  - Evaluate outputs with pluggable Evaluator implementations (single or multi-score)
  - Run multiple times to measure stability and aggregate scores

- BaseEvaluationTest
  - Orchestrates runs over your dataset of TestCase entries
  - Emits per-datapoint spans and run-level metadata (when tracing is enabled via ai-dev-kit-tracing)
  - Aggregates and logs scores at the end of each run

- NoLoggingEvaluationTest
  - Fully local/in-memory evaluation (no external logging)
  - Ideal for quick iteration in CI or local development

- LangfuseEvaluationTest
  - Logging to Langfuse
  - Ready-to-use provider is available in the [Langfuse module](ai-dev-kit-tracking-providers/ai-dev-kit-tracking-langfuse)

### 🚀 Getting Started
- Extend LangfuseEvaluationTest or NoLoggingEvaluationTest and implement the required hooks from your test class
- Provide a list of TestCase items, a Generator, and an Evaluator

For a detailed implementation guide, refer to the [Evaluation README](ai-dev-kit-eval/README.md) and two examples:

1. `HaikuGeneratorTest`: simple eval for LLM-written haikus that use LLM-as-a-Judge as a
   metric, [code in this repo](https://github.com/JetBrains/ai-dev-kit/blob/main/ai-dev-kit-example/src/test/kotlin/ai/dev/kit/example/haiku/HaikuGeneratorTest.kt)
2. `FindMiniAgentTest`: a simple eval for the file search
   agent [in the code engine repo](https://github.com/JetBrains/code-engine/pull/578/files#diff-701529c49dd319c5a627afaf6eb23ea130b44cfd6dce43d2051999a961afa6f6)

## 🏗️ Project Structure

#### 📦 Core Modules
- **ai-dev-kit-tracing**: Core tracing functionality with OpenTelemetry tracing support. Written in Kotlin Multiplatform (KMP). For a more details, refer to the [README.md](ai-dev-kit-tracing/README.md)
- **ai-dev-kit-eval**: Evaluation framework for AI models, supporting criteria-based testing and quality metrics.
- **ai-dev-kit-plugin** For a more detailed how-to, refer to the [README.md](ai-dev-kit-plugin/README.md) file in that submodule:
    - **trace-plugin**: Kotlin compiler plugin for tracing. Written in Kotlin Multiplatform (KMP).
    - **trace-gradle**: Gradle plugin

#### 📊 Tracking
- **ai-dev-kit-tracking-providers**: Integration modules for various tracking platforms, such as:

| Tracking Platform                                       | Tracing Support | Evaluation Support | Setup Guide                                                                            |
|---------------------------------------------------------|-----------------|--------------------|----------------------------------------------------------------------------------------|
| **[Weights & Biases](https://docs.wandb.ai/)**          | ✅               | ❌                  | [Weights & Biases docs](https://docs.wandb.ai/)                                        |
| **[Langfuse](https://github.com/langfuse/langfuse)**    | ✅               | ✅                  | [Setup Langfuse](ai-dev-kit-tracking-providers/ai-dev-kit-tracking-langfuse/README.md) |

#### 🛠️ Development Tools
- **ai-dev-kit-test-base**: Common test utilities and base classes for testing tracing capabilities
- **ai-dev-kit-example**: Example implementations and usage demonstrations

### 📦 How to publish `ai-dev-kit`
* When a pull request is created, a comment is automatically added with instructions for running the publishing build.
* To publish locally, you need to provide `SPACE_USERNAME` and `SPACE_PASSWORD` `.env` variables with write access 
to the `ai-dev-kit` [repository](https://jetbrains.team/p/ai-development-kit/packages/maven/ai-development-kit).
Then run the following command
```bash
./gradlew ai-dev-kit-trace-gradle:publish ai-dev-kit-trace-plugin:publish :publishContentModules
```
When a pull request is created, a comment is automatically added with instructions for running the publishing build.

### 🧪 Running Tests
The AI Development Kit uses test tags to manage which tests are executed in different environments.
Tests or test classes tagged with `SkipForNonLocal` are designed to run only in local environments.
When the `aiDevKitLocalTests` system property is set to `false` (the default is `true`), these tests are excluded from execution.
This ensures that tests tagged with `SkipForNonLocal` do not run on TeamCity or other non-local environments.

