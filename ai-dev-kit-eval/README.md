# 🧪 AI Dev Kit Evaluation Module

The ai-dev-kit-eval module provides a lightweight, extensible set of APIs for evaluating AI features and models. It helps you define test cases, run generators, compute scores, and optionally log results to external tracking systems.

## 🧩 Core Concepts
- TestCase: input + ground truth per example
- Generator: your AI functionality under test (suspend generating)
- Evaluator: computes EvalResult from ground truth and output
- EvaluationClient: optional external logging backend

## 🚀 Getting Started

Evaluation includes and relies on tracing.
In addition to adding the tracing dependency as explained in the main README, add the evaluation dependency:

```toml
[libraries]
ai-dev-kit-eval = { module = "com.jetbrains:ai-dev-kit-eval", version.ref = "ai-dev-kit" }
```

```kotlin
dependencies {
  implementation(libs.ai.dev.kit.eval)
}
```

In code, you will need to define some unified classes for the AI feature you're evaluating:

1. `class InputOfYourFeature : AIInput`, `class OutputOfYourFeature : AIOutput`
2. `class YourGT : GroundTruth` to add all the information necessary to score the output. If no information is needed,
   you can use the `NoGroundTruth` stub.
3. `class YourGenerator : Generator<InputOfYourFeature, OutputOfYourFeature>` that encapsulates your feature.
4. `class YourEvaluator : Evaluator<OutputOfYourFeature, YourGT, MultiScoreEvalResult>` that assess the output
5. `class YourEval : LangfuseEvaluationTest<InputOfYourFeature, YourGT, OutputOfYourFeature, MultiScoreEvalResult>`
   that uses `YourGenerator` internally to execute your AI feature and `YourEvaluator` to score the output.
   The eval dataset is passed by overriding the `testCases` field.
   
   You can use [NoLoggingEvaluationTest](ai-dev-kit-eval/src/main/kotlin/ai/dev/kit/eval/utils/NoLoggingEvaluationTest.kt) instead of [LangfuseEvaluationTest](ai-dev-kit-tracking-providers/ai-dev-kit-tracking-langfuse/src/main/kotlin/ai/dev/kit/providers/langfuse/LangfuseEvaluationTest.kt)
   if you would not like your results to be logged externally.

Once you have that, you can run `YourEval` as a usual JUnit test and view the results on Langfuse.
