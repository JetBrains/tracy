package ai.dev.kit.providers.mlflow.fluent

import ai.dev.kit.tracing.fluent.processor.TracingFlowProcessor
import ai.dev.kit.providers.mlflow.KotlinMlflowClient

/**
 * Sets up tracing with logging to Mlflow.
 *
 * @param userId any string.
 *               The user identifier for Mlflow.
 *               If not provided, the user ID will be derived from the environment variables or default settings.
 */
fun setupMlflowTracing(userId: String? = null) {
    KotlinMlflowClient.setupCredentials(userId)
    TracingFlowProcessor.setupTracing(MlflowTracingMetadataConfigurator(), MlflowTracePublisher())
}