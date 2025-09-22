package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.HttpClientLLMProvider
import ai.dev.kit.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.grazie.api.gateway.client.DefaultUrlResolver
import ai.grazie.api.gateway.client.PlatformConfigurationUrl
import ai.grazie.api.gateway.client.ResolutionResult
import ai.grazie.api.gateway.client.SuspendableAPIGatewayClient
import ai.grazie.client.common.SuspendableHTTPClient
import ai.grazie.client.ktor.GrazieKtorHTTPClient
import ai.grazie.model.auth.GrazieAgent
import ai.grazie.model.auth.v5.AuthData
import ai.grazie.model.cloud.AuthType
import ai.grazie.model.llm.profile.OpenAIProfileIDs
import ai.grazie.model.llm.prompt.LLMPromptID
import ai.grazie.utils.http.PlatformHttpClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

suspend fun configureClient(userToken: String = "???"): SuspendableAPIGatewayClient {
    val httpClient = SuspendableHTTPClient.WithV5(
        GrazieKtorHTTPClient(instrument(PlatformHttpClient.client("Default"), HttpClientLLMProvider.Grazie)),
        authData = AuthData(userToken, grazieAgent = GrazieAgent("api-request-example", "dev"))
    )
    val resolutionResult = DefaultUrlResolver(PlatformConfigurationUrl.Production.GLOBAL, httpClient).resolve()
    val serverUrl = when (resolutionResult) {
        is ResolutionResult.Failure -> {
            throw resolutionResult.problems.first()
        }

        is ResolutionResult.FallbackUrl -> {
            println(resolutionResult.problems)
            resolutionResult.url
        }

        is ResolutionResult.Success -> resolutionResult.url
    }
    return SuspendableAPIGatewayClient(
        serverUrl = serverUrl,
        authType = AuthType.User,
        httpClient = httpClient,
    )
}

suspend fun callToChat(client: SuspendableAPIGatewayClient) {
    // Call the feature
    val chatResponseStream = client.llm().v7().chat {
        prompt = LLMPromptID("pizza_prompt")
        profile = OpenAIProfileIDs.Chat.GPT4
        messages {
            system("You are a helpful assistant")
            user("When was the first version of IntelliJ IDEA released?")
        }
    }
    // Display the response
    chatResponseStream.collect {
        print(it.content)
    }
    val f = 4
}

@Tag("SkipForNonLocal")
class GrazieTracingTest : BaseOpenTelemetryTracingTest() {
    @Test
    fun `test OpenAI chat completions auto tracing`() = runTest {
        val grazie = configureClient()
        callToChat(grazie)
    }
}