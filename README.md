Test Change

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
* 🔌 Integration with `Langfuse`, `Mlflow` and `Weights & Biases`.
* 📊 Evaluation framework with test cases and evaluation criteria.
* 🤖 Internal `OpenAI` compatible gateway with `LiteLLM` support. For a more detailed description, refer to the [article](https://youtrack.jetbrains.com/articles/JBAI-A-659/LiteLLM-Internal-LLM-Gateway-for-Research-and-Experimentation)

## 📚 How to use?

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
ai-dev-kit-core = { module = "com.jetbrains:ai-dev-kit-core", version.ref = "ai-dev-kit" }

# You can choose from multiple tracing providers:
# - Langfuse (used in the example below)
# - MLflow
# - Weights & Biases (W&B)
ai-dev-kit-tracking-langfuse = { module = "com.jetbrains:ai-dev-kit-tracking-langfuse", version.ref = "ai-dev-kit" }
# ai-dev-kit-tracking-mlflow = { module = "com.jetbrains:ai-dev-kit-tracking-mlflow", version.ref = "ai-dev-kit" }
# ai-dev-kit-tracking-wandb = { module = "com.jetbrains:ai-dev-kit-tracking-wandb", version.ref = "ai-dev-kit" }

[plugins]
ai-dev-kit = { id = "ai.dev.kit.trace", version.ref = "ai-dev-kit-plugin" }
```
Replace `VERSION` with the latest version from the JetBrains repository.

#### 3. Add Dependencies
In your module's `build.gradle.kts`, add the required dependencies:

```kotlin
dependencies {
    implementation(libs.ai.dev.kit.core)
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
  (e.g., Weights & Biases, MLflow) by making the corresponding changes in your setup:
```kotlin
setupLangfuseTracing()
```
⚠️ Important: You must set up tracing before calling any annotated methods.
If tracing is not initialized beforehand, the tracking provider will not be defined and traces will not be recorded.

* Annotate traced function with `@KotlinFlowTrace`

## 🏗️ Project Structure

#### 📦 Core Modules
- **ai-dev-kit-core**: Core tracing functionality with OpenTelemetry tracing support. Written in Kotlin Multiplatform (KMP). For a more details, refer to the [README.md](ai-dev-kit-core/README.md)
- **ai-dev-kit-eval**: Evaluation framework for AI models, supporting criteria-based testing and quality metrics.
- **ai-dev-kit-plugin** For a more detailed how-to, refer to the [README.md](ai-dev-kit-plugin/README.md) file in that submodule:
    - **trace-plugin**: Kotlin compiler plugin for tracing. Written in Kotlin Multiplatform (KMP).
    - **trace-gradle**: Gradle plugin

#### 📊 Tracking
- **ai-dev-kit-tracking-providers**: Integration modules for various tracking platforms, such as:

| Tracking Platform                                       | Tracing Support | Evaluation Support | Setup Guide                                                                            |
|---------------------------------------------------------|-----------------|--------------------|----------------------------------------------------------------------------------------|
| **[Mlflow](https://mlflow.org/docs/latest/index.html)** | ✅               | ✅                  | [MLflow docs](https://mlflow.org/docs/latest/)                                         |
| **[Weights & Biases](https://docs.wandb.ai/)**          | ✅               | ❌                  | [Weights & Biases docs](https://docs.wandb.ai/)                                        |
| **[Langfuse](https://github.com/langfuse/langfuse)**    | ✅               | ❌                  | [Setup Langfuse](ai-dev-kit-tracking-providers/ai-dev-kit-tracking-langfuse/README.md) |

#### 🛠️ Development Tools
- **ai-dev-kit-test-base**: Common test utilities and base classes for testing tracing capabilities
- **ai-dev-kit-example**: Example implementations and usage demonstrations

#### 📦 How to publish `ai-dev-kit`
To publish,
you need
to provide `SPACE_USERNAME` and `SPACE_PASSWORD` `.env` variables with write access to the `ai-dev-kit` [repository](https://jetbrains.team/p/ai-development-kit/packages/maven/ai-development-kit).
Then run the following command
```bash
./gradlew ai-dev-kit-trace-gradle:publish ai-dev-kit-trace-plugin:publish :publishContentModules
```
