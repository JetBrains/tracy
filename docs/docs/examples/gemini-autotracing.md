
<!--- INCLUDE
import ai.jetbrains.tracy.openai.clients.instrument
import com.openai.client.okhttp.OpenAIOkHttpClient
import java.time.Duration

fun main() {
-->
<!--- SUFFIX
}
-->
```kotlin
val client = OpenAIOkHttpClient.builder()
    .baseUrl("llm-provider-url")
    .apiKey(System.getenv("API_KEY"))
    .timeout(Duration.ofSeconds(60))
    .build()


val instrumentedClient = instrument(client)
```
<!--- KNIT example-gemini-autotracing-01.kt -->
