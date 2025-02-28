# Features to Demo
1. MLflow logging from Kotlin
2. Model parameters capture
3. Dynamic test generation

# KotlinMlflowClient
1. In charge of providing current `experimentId` and `runId`

# Tracing setup
1. To make params during runtime visible, you need add `-java-parameters`
2. Setup `TracingFlowProcessor.setup()`
3. Does not work yet with suspend functions!
4. You need to provide package name for tracing (see `TracingFlowDecoratorAgent`)