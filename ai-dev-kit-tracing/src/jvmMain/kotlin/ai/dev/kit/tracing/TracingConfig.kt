package ai.dev.kit.tracing

import java.util.UUID
import kotlin.Long

sealed class TracingConfig(
    open val traceToConsole: Boolean = false,
    open val exporterTimeout: Long = 10
)

/**
 * Configuration for exporting OpenTelemetry traces to [Langfuse](https://langfuse.com).
 *
 * @param langfuseUrl The base URL of the Langfuse deployment.
 *        Example: `https://cloud.langfuse.com`.
 *        Defaults to the `LANGFUSE_URL` environment variable or `https://cloud.langfuse.com`.
 * @param langfusePublicKey The Langfuse public API key.
 *        Retrieved from `LANGFUSE_PUBLIC_KEY` env variable if not provided.
 * @param langfuseSecretKey The Langfuse secret API key.
 *        Retrieved from `LANGFUSE_SECRET_KEY` env variable if not provided.
 * @param userId A unique identifier for the user or session (e.g., UUID, user ID, etc.).
 *        If not provided, a random UUID will be generated as a default.
 * @param traceToConsole If `true`, also logs traces to the console (useful for local debugging).
 *        Default: `false`.
 * @param exporterTimeout Timeout in seconds for exporting spans.
 *        Default: `10`.
 *
 * @see <a href="https://langfuse.com/docs/opentelemetry/get-started">Langfuse OpenTelemetry Docs</a>
 */
data class LangfuseConfig(
    val langfuseUrl: String? = null,
    val langfusePublicKey: String? = null,
    val langfuseSecretKey: String? = null,
    val userId: String = UUID.randomUUID().toString(),
    override val traceToConsole: Boolean = false,
    override val exporterTimeout: Long = 10
) : TracingConfig(traceToConsole, exporterTimeout)

/**
 * Configuration for exporting OpenTelemetry traces to [W&B Weave](https://wandb.ai/site/weave).
 *
 * @param weaveOtelBaseUrl The base URL of the Weave OTLP endpoint.
 *        Defaults to the `WEAVE_URL` env variable or `https://trace.wandb.ai`.
 * @param weaveEntity The W&B entity (team/org).
 *        Retrieved from `WEAVE_ENTITY` env variable if not provided.
 * @param weaveProjectName The name of the W&B Weave project.
 *        Retrieved from `WEAVE_PROJECT_NAME` env variable if not provided.
 * @param weaveApiKey The W&B API key.
 *        Retrieved from `WEAVE_API_KEY` env variable if not provided.
 * @param traceToConsole If `true`, also logs traces to the console (useful for local debugging).
 *        Default: `false`.
 * @param exporterTimeout Timeout in seconds for exporting spans.
 *        Default: `10`.
 *
 * @see <a href="https://weave-docs.wandb.ai/guides/tracking/otel/">Weave OpenTelemetry Docs</a>
 */
data class WeaveConfig(
    val weaveOtelBaseUrl: String? = null,
    val weaveEntity: String? = null,
    val weaveProjectName: String? = null,
    val weaveApiKey: String? = null,
    override val traceToConsole: Boolean = false,
    override val exporterTimeout: Long = 10
) : TracingConfig(traceToConsole, exporterTimeout)
