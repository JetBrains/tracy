package ai.dev.kit.providers.wandb.fluent

import ai.dev.kit.tracing.fluent.processor.TracingFlowProcessor
import ai.dev.kit.providers.wandb.KotlinWandbClient
import ai.dev.kit.providers.wandb.WandbDiContainer

/**
 * Sets up tracing with logging to WandB (Weights & Biases).
 *
 * @param userId any string.
 *               The user identifier for WandB.
 *               If not provided, the user ID will be derived from the environment variables or default settings.
 * @param wandbUserApiKey your user API key.
 *               Take it or create a new one here https://wandb.ai/settings#api
 *               If not provided, the user ID will be derived from the environment variables or default settings.
 */
fun setupWandbTracing(userId: String? = null, wandbUserApiKey: String? = null) {
    KotlinWandbClient.setupCredentials(userId, wandbUserApiKey)
    TracingFlowProcessor.setupTracing(WandbDiContainer.di)
}