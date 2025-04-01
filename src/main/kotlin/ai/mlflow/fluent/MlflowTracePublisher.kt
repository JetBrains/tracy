package ai.mlflow.fluent

import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.serialization.json.Json
import ai.core.fluent.processor.TracePublisher
import org.example.ai.mlflow.*
import org.example.ai.mlflow.dataclasses.*

object MlflowTracePublisher : TracePublisher {
    override suspend fun publishTrace(traces: List<SpanData>) {
        val parentSpan: SpanData = traces.find { it.parentSpanId == SpanId.getInvalid() }
            ?: throw IllegalStateException("Parent span not found.")
        val traceCreationInfoJson = parentSpan.getAttribute(MlflowFluentSpanAttributes.TRACE_CREATION_INFO)

        val traceResponse: TraceInfo = traceCreationInfoJson?.let { Json.decodeFromString(TraceInfo.serializer(), it) }
            ?: throw IllegalStateException("Missing traceCreationInfo attribute in the parent span.")

        val rootInputs = parentSpan.getAttribute(MlflowFluentSpanAttributes.MLFLOW_SPAN_INPUTS)
        val rootResult = parentSpan.getAttribute(MlflowFluentSpanAttributes.MLFLOW_SPAN_OUTPUTS)

        updateTraceTags(
            requestId = traceResponse.requestId, updateTagRequest = traces.toUpdateTraceTagsRequest()
        )

        uploadTraceArtifacts(
            traceResponse.experimentId, traceResponse.requestId, SpanArtifactsRequest(
                spans = traces.toSpanArtifactsRequest(traceResponse.requestId),
                request = rootInputs,
                response = rootResult
            )
        )

        patchTrace(
            TracePatchRequest(
                requestId = traceResponse.requestId,
                status = "OK",
                timestampMs = parentSpan.endEpochNanos / 1_000_000,
                requestMetadata = listOf(
                    RequestMetadata("mlflow.trace_schema.version", "2"),
                    RequestMetadata("mlflow.traceInputs", rootInputs ?: "null"),
                    RequestMetadata("mlflow.traceOutputs", rootResult ?: "null")
                ),
                tags = listOf(
                    Tag(
                        "mlflow.source.name",
                        parentSpan.getAttribute(MlflowFluentSpanAttributes.MLFLOW_SPAN_SOURCE_NAME) ?: "null"
                    ), Tag("mlflow.source.type", "LOCAL"), Tag("mlflow.traceName", parentSpan.name)
                )
            )
        )
        patchTrace(
            TracePatchRequest(
                requestId = traceResponse.requestId,
                status = "OK",
                timestampMs = parentSpan.endEpochNanos / 1_000_000,
                requestMetadata = buildList {
                    add(RequestMetadata("mlflow.trace_schema.version", "2"))
                    rootInputs?.let { add(RequestMetadata("mlflow.traceInputs", it)) }
                    rootResult?.let { add(RequestMetadata("mlflow.traceOutputs", it)) }
                    parentSpan.getAttribute(MlflowFluentSpanAttributes.MLFLOW_SOURCE_RUN)?.let {
                        add(RequestMetadata("mlflow.sourceRun", it))
                    }
                },
                tags = buildList {
                    add(
                        Tag(
                            "mlflow.source.name",
                            parentSpan.getAttribute(MlflowFluentSpanAttributes.MLFLOW_SPAN_SOURCE_NAME) ?: "null"
                        )
                    )
                    add(Tag("mlflow.source.type", "LOCAL"))
                    add(Tag("mlflow.traceName", parentSpan.name))
                })
        )
    }
}
