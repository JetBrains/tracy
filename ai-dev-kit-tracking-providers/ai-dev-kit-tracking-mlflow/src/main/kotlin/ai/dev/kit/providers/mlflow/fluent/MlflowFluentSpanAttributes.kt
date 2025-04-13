package ai.dev.kit.providers.mlflow.fluent

import io.opentelemetry.api.common.AttributeKey

enum class MlflowFluentSpanAttributes(val key: String) {
    MLFLOW_SPAN_INPUTS("mlflow.spanInputs"),
    MLFLOW_SPAN_OUTPUTS("mlflow.spanOutputs"),
    MLFLOW_SOURCE_RUN("mlflow.sourceRun"),
    MLFLOW_SPAN_FUNCTION_NAME("mlflow.spanFunctionName"),
    MLFLOW_SPAN_SOURCE_NAME("mlflow.source.name"),
    MLFLOW_SPAN_TYPE("mlflow.spanType"),
    TRACE_CREATION_INFO("traceCreationInfo");

    fun asAttributeKey(): AttributeKey<String> = AttributeKey.stringKey(key)
}
