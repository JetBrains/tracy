plugins {
    id("org.jetbrains.dokka")
}

dokka {
    dokkaSourceSets.configureEach {
         includes.from("Module.md")

        pluginsConfiguration.html {
            footerMessage = "Copyright © 2000-2025 JetBrains s.r.o."
        }

        sourceLink {
            localDirectory = rootDir
            remoteUrl("https://github.com/JetBrains/ai-dev-kit/tree/main")
            remoteLineSuffix = "#L"
        }

        externalDocumentationLinks.register("ktor-client") {
            url("https://api.ktor.io/ktor-client/")
            packageListUrl("https://api.ktor.io/package-list")
        }

        externalDocumentationLinks.register("kotlinx-coroutines") {
            url("https://kotlinlang.org/api/kotlinx.coroutines/")
            packageListUrl("https://kotlinlang.org/api/kotlinx.coroutines/package-list")
        }

        externalDocumentationLinks.register("kotlinx-serialization") {
            url("https://kotlinlang.org/api/kotlinx.serialization/")
            packageListUrl("https://kotlinlang.org/api/kotlinx.serialization/package-list")
        }

        externalDocumentationLinks.register("okhttp3") {
            url("https://javadoc.io/doc/com.squareup.okhttp3/okhttp/latest/")
            packageListUrl("https://javadoc.io/doc/com.squareup.okhttp3/okhttp/latest/package-list")
        }

        // Open Telemetry Documentation
        externalDocumentationLinks.register("opentelemetry-api") {
            url("https://javadoc.io/doc/io.opentelemetry/opentelemetry-api/latest/")
            packageListUrl("https://javadoc.io/doc/io.opentelemetry/opentelemetry-api/latest/element-list")
        }

        externalDocumentationLinks.register("opentelemetry-sdk") {
            url("https://javadoc.io/doc/io.opentelemetry/opentelemetry-sdk/latest/")
            packageListUrl("https://javadoc.io/doc/io.opentelemetry/opentelemetry-sdk/latest/element-list")
        }

        externalDocumentationLinks.register("opentelemetry-exporter-otlp") {
            url("https://javadoc.io/doc/io.opentelemetry/opentelemetry-exporter-otlp/latest/")
            packageListUrl("https://javadoc.io/doc/io.opentelemetry/opentelemetry-exporter-otlp/latest/element-list")
        }

        externalDocumentationLinks.register("opentelemetry-exporter-logging") {
            url("https://javadoc.io/doc/io.opentelemetry/opentelemetry-exporter-logging/latest/")
            packageListUrl("https://javadoc.io/doc/io.opentelemetry/opentelemetry-exporter-logging/latest/element-list")
        }

        externalDocumentationLinks.register("opentelemetry-sdk-testing") {
            url("https://javadoc.io/doc/io.opentelemetry/opentelemetry-sdk-testing/latest/")
            packageListUrl("https://javadoc.io/doc/io.opentelemetry/opentelemetry-sdk-testing/latest/element-list")
        }

        // LLM Providers Documentations
        externalDocumentationLinks.register("openai-java") {
            url("https://javadoc.io/doc/com.openai/openai-java/latest/")
            packageListUrl("https://javadoc.io/doc/com.openai/openai-java/latest/element-list")
        }

        externalDocumentationLinks.register("google-genai") {
            url("https://javadoc.io/doc/com.google.genai/google-genai/latest/")
            packageListUrl("https://javadoc.io/doc/com.google.genai/google-genai/latest/element-list")
        }

        externalDocumentationLinks.register("com.anthropic:anthropic-java") {
            url("https://javadoc.io/doc/com.anthropic/anthropic-java/latest/")
            packageListUrl("https://javadoc.io/doc/com.anthropic/anthropic-java/latest/element-list")
        }
    }
}