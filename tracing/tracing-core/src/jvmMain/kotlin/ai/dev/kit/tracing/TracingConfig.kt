package ai.dev.kit.tracing

import kotlin.Long

const val MAX_NUMBER_OF_SPAN_ATTRIBUTES = 256
const val MAX_SPAN_ATTRIBUTE_VALUE_LENGTH = 8192

sealed class TracingConfig(
    open val traceToConsole: Boolean = false,
    open val exporterTimeout: Long = 10,
    open val maxNumberOfSpanAttributes: Int? = null,
    open val maxSpanAttributeValueLength: Int? = null
)

/**
 * Configuration for exporting OpenTelemetry traces to [Langfuse](https://langfuse.com).
 *
 * @param langfuseUrl The base URL of the Langfuse deployment.
 *  Example: `https://cloud.langfuse.com`.
 *  Defaults to the `LANGFUSE_URL` environment variable or [ai.dev.kit.exporters.LANGFUSE_BASE_URL].
 * @param langfusePublicKey The Langfuse public API key.
 *  Retrieved from `LANGFUSE_PUBLIC_KEY` env variable if not provided.
 * @param langfuseSecretKey The Langfuse secret API key.
 *  Retrieved from `LANGFUSE_SECRET_KEY` env variable if not provided.
 * @param traceToConsole If `true`, also logs traces to the console (useful for local debugging).
 *  Default: `false`.
 * @param exporterTimeout Timeout in seconds for exporting spans.
 *  Default: `10`.
 * @param maxNumberOfSpanAttributes max number of attributes per Span.
 *  Defaults to the `MAX_NUMBER_OF_SPAN_ATTRIBUTES` environment variable or [MAX_NUMBER_OF_SPAN_ATTRIBUTES] variable.
 * @param maxSpanAttributeValueLength max number of characters for attribute strings.
 *  Defaults to the `MAX_SPAN_ATTRIBUTE_VALUE_LENGTH` environment variable or [MAX_SPAN_ATTRIBUTE_VALUE_LENGTH]variable.
 *
 * @see <a href="https://langfuse.com/docs/opentelemetry/get-started">Langfuse OpenTelemetry Docs</a>
 */
data class LangfuseConfig(
    val langfuseUrl: String? = null,
    val langfusePublicKey: String? = null,
    val langfuseSecretKey: String? = null,
    override val traceToConsole: Boolean = false,
    override val exporterTimeout: Long = 10,
    override val maxNumberOfSpanAttributes: Int? = null,
    override val maxSpanAttributeValueLength: Int? = null
) : TracingConfig(traceToConsole, exporterTimeout, maxNumberOfSpanAttributes, maxSpanAttributeValueLength)

/**
 * Configuration for exporting OpenTelemetry traces to [W&B Weave](https://wandb.ai/site/weave).
 *
 * @param weaveOtelBaseUrl The base URL of the Weave OTLP endpoint.
 *  Defaults to the `WEAVE_URL` env variable or [ai.dev.kit.exporters.WEAVE_BASE_URL].
 * @param weaveEntity The W&B entity (team/org).
 *  Retrieved from `WEAVE_ENTITY` env variable if not provided.
 * @param weaveProjectName The name of the W&B Weave project.
 *  Retrieved from `WEAVE_PROJECT_NAME` env variable if not provided.
 * @param weaveApiKey The W&B API key.
 *  Retrieved from `WEAVE_API_KEY` env variable if not provided.
 * @param traceToConsole If `true`, also logs traces to the console (useful for local debugging).
 *  Default: `false`.
 * @param exporterTimeout Timeout in seconds for exporting spans.
 *  Default: `10`.
 * @param maxNumberOfSpanAttributes max number of attributes per Span.
 *  Defaults to the `MAX_NUMBER_OF_SPAN_ATTRIBUTES` environment variable or [MAX_NUMBER_OF_SPAN_ATTRIBUTES] variable.
 * @param maxSpanAttributeValueLength max number of characters for attribute strings.
 *  Defaults to the `MAX_SPAN_ATTRIBUTE_VALUE_LENGTH` environment variable or [MAX_SPAN_ATTRIBUTE_VALUE_LENGTH] variable.
 *
 * @see <a href="https://weave-docs.wandb.ai/guides/tracking/otel/">Weave OpenTelemetry Docs</a>
 */
data class WeaveConfig(
    val weaveOtelBaseUrl: String? = null,
    val weaveEntity: String? = null,
    val weaveProjectName: String? = null,
    val weaveApiKey: String? = null,
    override val traceToConsole: Boolean = false,
    override val exporterTimeout: Long = 10,
    override val maxNumberOfSpanAttributes: Int? = null,
    override val maxSpanAttributeValueLength: Int? = null
) : TracingConfig(traceToConsole, exporterTimeout, maxNumberOfSpanAttributes, maxSpanAttributeValueLength)

/**
 * Configuration for exporting OpenTelemetry traces to a file.
 *
 * @param filepath a filepath to which traces should be written (e.g., `tracing.log` or `traces.jsonl`).
 * @param append whether to append to a file if it exists or create anew.
 * @param format the format in which to log traces to the console.
 * @param traceToConsole If true, also logs traces to the console (useful for local debugging).
 *        Default: false.
 * @param maxNumberOfSpanAttributes max number of attributes per Span.
 *  Defaults to the `MAX_NUMBER_OF_SPAN_ATTRIBUTES` environment variable or [MAX_NUMBER_OF_SPAN_ATTRIBUTES] variable.
 * @param maxSpanAttributeValueLength max number of characters for attribute strings.
 *  Defaults to the `MAX_SPAN_ATTRIBUTE_VALUE_LENGTH` environment variable or [MAX_SPAN_ATTRIBUTE_VALUE_LENGTH] variable.
 */
data class FileConfig(
    val filepath: String,
    val append: Boolean,
    val format: OutputFormat = OutputFormat.PLAIN_TEXT,
    override val traceToConsole: Boolean = false,
    override val exporterTimeout: Long = 10,
    override val maxNumberOfSpanAttributes: Int? = null,
    override val maxSpanAttributeValueLength: Int? = null
) : TracingConfig(
    traceToConsole = traceToConsole,
    exporterTimeout = exporterTimeout,
    maxNumberOfSpanAttributes = maxNumberOfSpanAttributes,
    maxSpanAttributeValueLength = maxSpanAttributeValueLength,
)

/**
 * Configuration for exporting OpenTelemetry traces to console only.
 *
 * @param format the format in which to log traces to the console.
 * @param traceToConsole If true, also logs traces to the console (useful for local debugging).
 * Default: true.
 * @param maxNumberOfSpanAttributes max number of attributes per Span.
 *  Defaults to the `MAX_NUMBER_OF_SPAN_ATTRIBUTES` environment variable or [MAX_NUMBER_OF_SPAN_ATTRIBUTES] variable.
 * @param maxSpanAttributeValueLength max number of characters for attribute strings.
 *  Defaults to the `MAX_SPAN_ATTRIBUTE_VALUE_LENGTH` environment variable or [MAX_SPAN_ATTRIBUTE_VALUE_LENGTH] variable.
 */
data class ConsoleConfig(
    val format: OutputFormat = OutputFormat.PLAIN_TEXT,
    override val traceToConsole: Boolean = true,
    override val exporterTimeout: Long = 10,
    override val maxNumberOfSpanAttributes: Int? = null,
    override val maxSpanAttributeValueLength: Int? = null
) : TracingConfig(
    traceToConsole = traceToConsole,
    exporterTimeout = exporterTimeout,
    maxNumberOfSpanAttributes = maxNumberOfSpanAttributes,
    maxSpanAttributeValueLength = maxSpanAttributeValueLength
)

/**
 * The format in which to log traces in the console.
 */
enum class OutputFormat {
    PLAIN_TEXT,
    JSON,
}
