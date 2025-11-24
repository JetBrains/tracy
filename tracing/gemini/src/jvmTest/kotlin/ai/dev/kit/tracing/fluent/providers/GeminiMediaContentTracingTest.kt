package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import com.google.genai.types.Content
import com.google.genai.types.GenerateImagesConfig
import com.google.genai.types.ImageConfig
import com.google.genai.types.Part
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Paths
import com.google.genai.types.GenerateContentConfig as GeminiGenerateContentConfig


// TODO: fix
// require the provider to be LiteLLM
@EnabledIfEnvironmentVariable(
    named = "LLM_PROVIDER_URL",
    matches = "https://litellm.labs.jb.gg",
    disabledReason = "LLM_PROVIDER_URL environment variable is not https://litellm.labs.jb.gg",
)
@Tag("gemini")
class GeminiMediaContentTracingTest : BaseGeminiTracingTest() {
    @Test
    fun `test1(TextToImage) generate image`() = runTest {
        val client = instrument(createGeminiClient())

        val model = "gemini-2.5-flash-image"
        val params = GeminiGenerateContentConfig.builder()
            .responseModalities("TEXT", "IMAGE")
            .build()

        val response = client.models.generateContent(
            model,
            "Create two pictures of a nano banana dish in a fancy restaurant with a Gemini theme",
            params,
        )

        for ((index, part) in response.parts()!!.withIndex()) {
            if (part.text().isPresent) {
                println(part.text().get())
            }
            else if (part.inlineData().isPresent) {
                val blob = part.inlineData().get()

                if (blob.data().isPresent) {
                    Files.write(Paths.get("${index}_generated_image.png"), blob.data().get());
                }
            }
        }
    }

    @Test
    fun `test2(TextAndImageToImage) generate image from reference`() = runTest {
        val client = instrument(createGeminiClient())

        val model = "gemini-2.5-flash-image"
        val params = GeminiGenerateContentConfig.builder()
            .responseModalities("TEXT", "IMAGE")
            .build()

        val prompt = Content.fromParts(
            Part.fromText("Replace dogs with cats in this image"),
            Part.fromBytes(
                readResource("image.jpg").readAllBytes(),
                "image/jpeg",
            )
        )

        val response = client.models.generateContent(
            model,
            prompt,
            params,
        )

        for ((index, part) in response.parts()!!.withIndex()) {
            if (part.text().isPresent) {
                println(part.text().get())
            }
            else if (part.inlineData().isPresent) {
                val blob = part.inlineData().get()
                if (blob.data().isPresent) {
                    Files.write(Paths.get("${index}_gemini_generated_image.png"), blob.data().get());
                }
            }
        }
    }

    @Test
    fun `test3(MultiturnImageEditing) generate image in chat`() = runTest {
        val client = instrument(createGeminiClient())

        val model = "gemini-2.5-flash-image"
        val params = GeminiGenerateContentConfig.builder()
            .responseModalities("TEXT", "IMAGE")
            .build()

        val chat = client.chats.create(model, params)
        val response = chat.sendMessage("Create a vibrant infographic that explains photosynthesis")

        for ((index, part) in response.parts()!!.withIndex()) {
            if (part.text().isPresent) {
                println(part.text().get())
            } else if (part.inlineData().isPresent) {
                val blob = part.inlineData().get()
                if (blob.data().isPresent) {
                    Files.write(Paths.get("${index}_photosynthesis.png"), blob.data().get())
                }
            }
        }
    }

    @Test
    fun `test4(MultiturnImageEditing) generate image in chat multi-turn`() = runTest {
        val client = instrument(createGeminiClient())

        val model = "gemini-2.5-flash-image"
        val params = GeminiGenerateContentConfig.builder()
            .responseModalities("TEXT", "IMAGE")
            .build()

        val chat = client.chats.create(model, params)

        chat.sendMessage("Create a vibrant infographic that explains photosynthesis")
        val response = chat.sendMessage("Update this infographic to be in Japanese")

        for ((index, part) in response.parts()!!.withIndex()) {
            if (part.text().isPresent) {
                println(part.text().get())
            } else if (part.inlineData().isPresent) {
                val blob = part.inlineData().get()
                if (blob.data().isPresent) {
                    Files.write(Paths.get("${index}_photosynthesis_jp.png"), blob.data().get())
                }
            }
        }
    }

    @Test
    fun `test5(HiRes) generate image in high resolution`() = runTest {
        val client = instrument(createGeminiClient())

        val model = "gemini-2.5-flash-image"
        val params = GeminiGenerateContentConfig.builder()
            .responseModalities("TEXT", "IMAGE")
            .imageConfig(ImageConfig.builder()
                .aspectRatio("16:9")
                .imageSize("4K")
                .build())
            .build()

        val response = client.models.generateContent(
            model,
            "Generate a cat on the table",
            params,
        )

        for ((index, part) in response.parts()!!.withIndex()) {
            if (part.text().isPresent) {
                println(part.text().get())
            } else if (part.inlineData().isPresent) {
                val blob = part.inlineData().get()
                if (blob.data().isPresent) {
                    Files.write(Paths.get("${index}_hires.png"), blob.data().get())
                }
            }
        }
    }

    @Test
    fun `test6(AudioUpload) understand audio file`() = runTest {
        val client = instrument(createGeminiClient())

        val model = "gemini-2.5-flash"
        val params = GeminiGenerateContentConfig.builder()
            .responseModalities("TEXT")
            .build()

        val prompt = Content.fromParts(
            Part.fromText("Tell me what you hear in the audio file"),
            Part.fromBytes(
                readResource("lofi.mp3").readAllBytes(),
                "audio/mp3",
            )
        )

        val response = client.models.generateContent(model, prompt, params)

        for (part in response.parts()!!) {
            if (part.text().isPresent) {
                println(part.text().get())
            }
        }
    }

    @Test
    fun `test6(AudioUpload) Imagen`() = runTest {
        val client = instrument(createGeminiClient())

        val model = "imagen-4.0-generate-001"
        val params = GenerateImagesConfig.builder()
            .enhancePrompt(true)
            .language("Korean")
            .numberOfImages(3)
            .build()

        val prompt = "Robot holding a red skateboard with a word 'hello' but in Korean."

        val response = client.models.generateImages(model, prompt, params)

        for ((index, part) in response.images()!!.withIndex()) {
            Files.write(Paths.get("${index}_imagen.png"), part.imageBytes().get())
        }
    }
}