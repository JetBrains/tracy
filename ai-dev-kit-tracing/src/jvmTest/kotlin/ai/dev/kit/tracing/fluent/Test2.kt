package ai.dev.kit.tracing.fluent

import ai.dev.kit.exporters.createLangfuseExporter
import ai.dev.kit.instrument
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit


class Test2 {
    private val langfuseUrl = "https://langfuse.labs.jb.gg"
    private val privateKey = "sk-lf-82c2a683-268f-4278-9f94-5e3dcc5a75bb"
    private val publicKey = "pk-lf-44c183d1-1914-46a1-bd4a-8377c2f437cc"

    @Test
    fun test3() {
        /*
        val credentials = "$publicKey:$privateKey"
        val encodedAuth = Base64.getEncoder().encodeToString(credentials.toByteArray())
        println("LANGFUSE_AUTH: $encodedAuth")

        // Step 2: Configure the exporter without Content-Type header
        val spanExporter = OtlpHttpSpanExporter.builder()
            .setEndpoint("https://langfuse.labs.jb.gg/api/public/otel/v1/traces")
            .addHeader("Authorization", "Basic $encodedAuth")
            // Remove the Content-Type header - let the exporter set it automatically
            .build()
        */

        val spanExporter = createLangfuseExporter()
            ?: error("Failed to create OpenTelemetry exporter for Langfuse")

        println("CREATING CUSTOM spanExporter: $spanExporter")

        // Step 3: Create tracer provider with proper resource attributes
        val resource = Resource.getDefault().merge(
            Resource.create(
                Attributes.builder()
                    // Add service identification - this helps Langfuse organize traces
                    .put("service.name", "ai-devkit-test")
                    .put("service.version", "1.0.0")
                    .build()
            )
        )

        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(resource)
            .build()

        val telemetry = OpenTelemetrySdk.builder()
            .setLoggerProvider(SdkLoggerProvider.builder().build())
            .setTracerProvider(tracerProvider)
            .build()

        // Step 4: Create and manage the span exactly like Python's "with" statement
        val tracer = telemetry.getTracer("ai.devkit.test")
        val span = tracer.spanBuilder("222 GenAI Attributes 12345!!!sdsdfsdfsdfsdf").startSpan()

        // The Python "with" statement makes the span current automatically
        span.makeCurrent().use { scope ->
            try {
                // Set attributes exactly like Python - notice the order and values
                span.setAttribute("gen_ai.prompt.0.role", "system")
                span.setAttribute("gen_ai.prompt.0.content", "You are a coding assistant that helps write Python code.")
                span.setAttribute("gen_ai.prompt.1.role", "user")
                span.setAttribute("gen_ai.prompt.1.content", "Write a function that calculates the factorial of a number.")

                span.setAttribute("gen_ai.completion.0.role", "assistant")
                span.setAttribute("gen_ai.completion.0.content", """def factorial(n):
    if n == 0:
        return 1
    return n * factorial(n-1)""")

                span.setAttribute("gen_ai.request.model", "gpt-4")
                span.setAttribute("gen_ai.request.temperature", 0.7)
                span.setAttribute("gen_ai.usage.prompt_tokens", 25)
                span.setAttribute("gen_ai.usage.completion_tokens", 45)

            } finally {
                span.end()
            }
        }

        // Step 5: CRITICAL - Force the export to complete before method ends
        // This is what your test was missing
        try {
            // Force flush all pending spans with a reasonable timeout
            val flushResult = tracerProvider.forceFlush()
            val success = flushResult.join(10, TimeUnit.SECONDS)
            println("Flush completed successfully: ${success.isSuccess}")
        } catch (e: Exception) {
            println("Error during flush: ${e.message}")
        }
    }

    @Test
    fun `test OpenAI auto tracing`() = runTest {
        val client = instrument(createLiteLLMClient())
        val params = ChatCompletionCreateParams.Companion.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(ChatModel.Companion.GPT_3_5_TURBO_0125).temperature(1.1).build()
        client.chat().completions().create(params)
    }

    private fun createLiteLLMClient(): OpenAIClient {
        return OpenAIOkHttpClient.builder()
            .baseUrl(LITELLM_URL)
            .apiKey(System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set"))
            .timeout(Duration.ofSeconds(60))
            .build()
    }
}