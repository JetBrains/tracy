
<!--- INCLUDE
import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.LITELLM_URL
import com.openai.client.okhttp.OpenAIOkHttpClient
import java.time.Duration

fun main() {
-->
<!--- SUFFIX
}
-->
```kotlin
val client = OpenAIOkHttpClient.builder()
    .baseUrl(LITELLM_URL)
    .apiKey(System.getenv("API_KEY"))
    .timeout(Duration.ofSeconds(60))
    .build()


val instrumentedClient = instrument(
    client
)
```
<!--- KNIT example-gemini-autotracing-01.kt -->
