package ai.dev.kit.providers.mlflow.fluent

import ai.dev.kit.tracing.fluent.FluentSpanAttributes
import ai.dev.kit.tracing.fluent.dataclasses.RequestMetadata
import ai.dev.kit.tracing.fluent.dataclasses.Tag
import ai.dev.kit.tracing.fluent.dataclasses.TraceInfo
import ai.dev.kit.tracing.fluent.getAttribute
import ai.dev.kit.tracing.fluent.processor.TracePublisher
import ai.dev.kit.providers.mlflow.dataclasses.SpanArtifactsRequest
import ai.dev.kit.providers.mlflow.dataclasses.TracePatchRequest
import ai.dev.kit.providers.mlflow.dataclasses.toSpanArtifactsRequest
import ai.dev.kit.providers.mlflow.dataclasses.toUpdateTraceTagsRequest
import ai.dev.kit.providers.mlflow.patchTrace
import ai.dev.kit.providers.mlflow.updateTraceTags
import ai.dev.kit.providers.mlflow.uploadTraceArtifacts
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.serialization.json.Json

class MlflowTracePublisher : TracePublisher {
    override suspend fun publishTrace(trace: List<SpanData>, tags: List<String>?) {
        val parentSpan: SpanData = trace.find { it.parentSpanId == SpanId.getInvalid() }
            ?: throw IllegalStateException("Parent span not found.")
        val traceCreationInfoJson = parentSpan.getAttribute(FluentSpanAttributes.TRACE_CREATION_INFO)

        val traceResponse: TraceInfo = traceCreationInfoJson?.let { Json.decodeFromString(TraceInfo.serializer(), it) }
            ?: throw IllegalStateException("Missing traceCreationInfo attribute in the parent span.")

        val rootInputs = parentSpan.getAttribute(FluentSpanAttributes.SPAN_INPUTS)
        val rootResult = parentSpan.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS)

        updateTraceTags(
            requestId = traceResponse.requestId, updateTagRequest = trace.toUpdateTraceTagsRequest()
        )

        uploadTraceArtifacts(
            traceResponse.experimentId, traceResponse.requestId, SpanArtifactsRequest(
                spans = trace.toSpanArtifactsRequest(traceResponse.requestId),
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
                    RequestMetadata("trace_schema.version", "2"),
                    RequestMetadata("traceInputs", rootInputs ?: "null"),
                    RequestMetadata("traceOutputs", rootResult ?: "null")
                ),
                tags = listOf(
                    Tag(
                        "source.name",
                        parentSpan.getAttribute(FluentSpanAttributes.SPAN_SOURCE_NAME) ?: "null"
                    ),
                    Tag("source.type", "LOCAL"),
                    Tag("traceName", parentSpan.name)
                )
            )
        )
        patchTrace(
            TracePatchRequest(
                requestId = traceResponse.requestId,
                status = "OK",
                timestampMs = parentSpan.endEpochNanos / 1_000_000,
                requestMetadata = buildList {
                    add(RequestMetadata("trace_schema.version", "2"))
                    rootInputs?.let { add(RequestMetadata("traceInputs", it)) }
                    rootResult?.let { add(RequestMetadata("traceOutputs", it)) }
                    parentSpan.getAttribute(FluentSpanAttributes.SOURCE_RUN)?.let {
                        add(RequestMetadata("sourceRun", it))
                    }
                },
                tags = buildList {
                    add(
                        Tag(
                            "source.name",
                            parentSpan.getAttribute(FluentSpanAttributes.SPAN_SOURCE_NAME) ?: "null"
                        )
                    )
                    add(Tag("source.type", "LOCAL"))
                    add(Tag("traceName", parentSpan.name))
                })
        )
    }
}
