
plugins {
    id("org.jetbrains.dokka")
}

private fun Project.versionOf(name: String): String {
    val catalogs = extensions.getByType(VersionCatalogsExtension::class.java)
    val libs = catalogs.named("libs")
    return libs.findVersion(name).get().requiredVersion
}

private class Versions {
    val okhttp = "latest" // NOTE: the actual version used doesn't have a package list published, using latest
    val opentelemetry = project.versionOf("opentelemetry")
    val openai = project.versionOf("openai")
    val gemini = project.versionOf("gemini")
    val anthropic = project.versionOf("anthropic")
}

dokka {
    val versions = Versions()

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
            url("https://javadoc.io/doc/com.squareup.okhttp3/okhttp/${versions.okhttp}/")
            packageListUrl("https://javadoc.io/doc/com.squareup.okhttp3/okhttp/${versions.okhttp}/package-list")
        }

        // Open Telemetry Documentation
        externalDocumentationLinks.register("opentelemetry-api") {
            url("https://javadoc.io/doc/io.opentelemetry/opentelemetry-api/${versions.opentelemetry}/")
            packageListUrl("https://javadoc.io/doc/io.opentelemetry/opentelemetry-api/${versions.opentelemetry}/element-list")
        }

        externalDocumentationLinks.register("opentelemetry-sdk") {
            url("https://javadoc.io/doc/io.opentelemetry/opentelemetry-sdk/${versions.opentelemetry}/")
            packageListUrl("https://javadoc.io/doc/io.opentelemetry/opentelemetry-sdk/${versions.opentelemetry}/element-list")
        }

        externalDocumentationLinks.register("opentelemetry-exporter-otlp") {
            url("https://javadoc.io/doc/io.opentelemetry/opentelemetry-exporter-otlp/${versions.opentelemetry}/")
            packageListUrl("https://javadoc.io/doc/io.opentelemetry/opentelemetry-exporter-otlp/${versions.opentelemetry}/element-list")
        }

        externalDocumentationLinks.register("opentelemetry-exporter-logging") {
            url("https://javadoc.io/doc/io.opentelemetry/opentelemetry-exporter-logging/${versions.opentelemetry}/")
            packageListUrl("https://javadoc.io/doc/io.opentelemetry/opentelemetry-exporter-logging/${versions.opentelemetry}/element-list")
        }

        externalDocumentationLinks.register("opentelemetry-sdk-testing") {
            url("https://javadoc.io/doc/io.opentelemetry/opentelemetry-sdk-testing/${versions.opentelemetry}/")
            packageListUrl("https://javadoc.io/doc/io.opentelemetry/opentelemetry-sdk-testing/${versions.opentelemetry}/element-list")
        }

        // LLM Providers Documentations
        externalDocumentationLinks.register("openai-java") {
            url("https://javadoc.io/doc/com.openai/openai-java/${versions.openai}/")
            packageListUrl("https://javadoc.io/doc/com.openai/openai-java/${versions.openai}/element-list")
        }

        externalDocumentationLinks.register("google-genai") {
            url("https://javadoc.io/doc/com.google.genai/google-genai/${versions.gemini}/")
            packageListUrl("https://javadoc.io/doc/com.google.genai/google-genai/${versions.gemini}/element-list")
        }

        externalDocumentationLinks.register("com.anthropic:anthropic-java") {
            url("https://javadoc.io/doc/com.anthropic/anthropic-java/${versions.anthropic}/")
            packageListUrl("https://javadoc.io/doc/com.anthropic/anthropic-java/${versions.anthropic}/element-list")
        }
    }
}