package ai.dev.kit.adapters.openai

import ai.dev.kit.http.protocol.Request
import ai.dev.kit.http.protocol.Response
import io.opentelemetry.api.trace.Span

/**
 * Extracts request/response bodies of Image Edit API.
 *
 * See [Image Edit API](https://platform.openai.com/docs/api-reference/images/createEdit)
 */
class ImagesEditsHandler : OpenAIApiHandler {
    override fun handleRequestAttributes(span: Span, request: Request) {
        TODO("Not yet implemented")
    }

    override fun handleResponseAttributes(span: Span, response: Response) {
        TODO("Not yet implemented")
    }

    override fun handleStreaming(span: Span, events: String) {
        TODO("Not yet implemented")
    }
}