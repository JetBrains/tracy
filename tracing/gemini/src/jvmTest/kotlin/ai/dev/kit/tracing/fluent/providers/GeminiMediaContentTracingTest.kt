package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.MediaContentAttributeValues
import ai.dev.kit.tracing.MediaSource
import ai.dev.kit.tracing.toMediaContentAttributeValues
import com.google.genai.types.Content
import com.google.genai.types.GenerateImagesConfig
import com.google.genai.types.ImageConfig
import com.google.genai.types.Part
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertEquals
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

        client.models.generateContent(
            model,
            "Create two pictures of a nano banana dish in a fancy restaurant with a Gemini theme",
            params,
        )

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = "image/png",
            data = null,
        )
        verifyMediaContentUploadAttributes(trace, expected = listOf(
            expectedImage
        ))
    }

    @Test
    fun `test2(TextAndImageToImage) generate image from reference`() = runTest {
        val client = instrument(createGeminiClient())

        val model = "gemini-2.5-flash-image"
        val params = GeminiGenerateContentConfig.builder()
            .responseModalities("TEXT", "IMAGE")
            .build()

        val image = MediaSource.File("image.jpg", "image/jpeg")

        val prompt = Content.fromParts(
            Part.fromText("Replace dogs with cats in this image"),
            Part.fromBytes(readResource(image.filepath).readAllBytes(), "image/jpeg")
        )

        client.models.generateContent(
            model,
            prompt,
            params,
        )

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = "image/png",
            data = null,
        )
        verifyMediaContentUploadAttributes(trace, expected = listOf(
            image.toMediaContentAttributeValues(field = "input"),
            expectedImage,
        ))
    }

    @Test
    fun `test3(MultiturnImageEditing) generate image in chat`() = runTest {
        val client = instrument(createGeminiClient())

        val model = "gemini-2.5-flash-image"
        val params = GeminiGenerateContentConfig.builder()
            .responseModalities("TEXT", "IMAGE")
            .build()

        val chat = client.chats.create(model, params)
        chat.sendMessage("Create a vibrant infographic that explains photosynthesis")

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = "image/png",
            data = null,
        )
        verifyMediaContentUploadAttributes(trace, expected = listOf(
            expectedImage,
        ))
    }

    @Test
    fun `test4(MultiturnImageEditing) generate image in chat multi-turn`() = runTest {
        val client = instrument(createGeminiClient())

        val model = "gemini-2.5-flash-image"
        val params = GeminiGenerateContentConfig.builder()
            .responseModalities("TEXT", "IMAGE")
            .build()

        val chat = client.chats.create(model, params)

        // expect two images to be generated
        chat.sendMessage("Create a vibrant infographic that explains photosynthesis")
        chat.sendMessage("Update this infographic to be in Japanese")

        val traces = analyzeSpans()
        assertEquals(2, traces.size)

        val trace1 = traces.first()
        val trace2 = traces.last()

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = "image/png",
            data = null,
        )

        verifyMediaContentUploadAttributes(trace1, expected = listOf(
            expectedImage
        ))
        // the first image becomes an input
        verifyMediaContentUploadAttributes(trace2, expected = listOf(
            expectedImage.copy(field = "input"),
            expectedImage,
        ))
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

        client.models.generateContent(
            model,
            "Generate a cat on the table",
            params,
        )

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = "image/png",
            data = null,
        )
        verifyMediaContentUploadAttributes(trace, expected = listOf(
            expectedImage,
        ))
    }

    @Test
    fun `test6(AudioUpload) understand audio file`() = runTest {
        val client = instrument(createGeminiClient())

        val model = "gemini-2.5-flash"
        val params = GeminiGenerateContentConfig.builder()
            .responseModalities("TEXT")
            .build()

        val file = MediaSource.File("lofi.mp3", "audio/mp3")

        val prompt = Content.fromParts(
            Part.fromText("Tell me what you hear in the audio file"),
            Part.fromBytes(
                readResource(file.filepath).readAllBytes(),
                file.contentType,
            )
        )

        val response = client.models.generateContent(model, prompt, params)

        for (part in response.parts()!!) {
            if (part.text().isPresent) {
                println(part.text().get())
            }
        }

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            file.toMediaContentAttributeValues(field = "input"),
        ))
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

        client.models.generateImages(model, prompt, params)

        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.first()

        println("attributes:\n ${trace.attributes}")

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = "image/png",
            data = null,
        )
        verifyMediaContentUploadAttributes(trace, expected = listOf(
            expectedImage, expectedImage, expectedImage
        ))
    }

    // TODO: upscaleImage, editImage
}