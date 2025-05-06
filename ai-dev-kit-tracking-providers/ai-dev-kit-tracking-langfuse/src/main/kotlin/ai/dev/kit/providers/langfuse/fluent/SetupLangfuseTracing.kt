package ai.dev.kit.providers.langfuse.fluent

import ai.dev.kit.core.fluent.processor.TracingFlowProcessor
import ai.dev.kit.providers.langfuse.KotlinLangfuseClient
import ai.dev.kit.providers.langfuse.LangfuseDiContainer

/**
 * Sets up tracing with logging to Langfuse.
 *
 * @param userId any string.
 *               The user identifier for WandB.
 *               If not provided, the user ID will be derived from the environment variables or default settings.
 * @param langfuseSecretKey your user secret API key.
 *               Take it or create a new one here https://langfuse.labs.jb.gg/project/{your_project_id}/settings/api-keys
 *               If not provided, the user ID will be derived from the environment variables or default settings.
 * @param langfusePublicKey your user public API key.
 *               Take it or create a new one here https://langfuse.labs.jb.gg/project/{your_project_id}/settings/api-keys
 *               If not provided, the user ID will be derived from the environment variables or default settings.
 */
fun setupLangfuseTracing(userId: String? = null, langfuseSecretKey: String? = null, langfusePublicKey: String? = null) {
    KotlinLangfuseClient.setupCredentials(userId, langfuseSecretKey, langfusePublicKey)
    TracingFlowProcessor.setupTracing(LangfuseDiContainer.di)
}