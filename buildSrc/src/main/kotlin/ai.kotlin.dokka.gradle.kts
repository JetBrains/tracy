/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("org.jetbrains.dokka")
    id("org.jetbrains.dokka-javadoc")
    `maven-publish`
}

private fun Project.versionOf(name: String): String {
    val catalogs = extensions.getByType(VersionCatalogsExtension::class.java)
    val libs = catalogs.named("libs")
    return libs.findVersion(name).get().requiredVersion
}

private fun Project.getVersions() = object {
    // NOTE: the actual version used doesn't have a package list published, using latest
    val okhttp = "latest"
    val opentelemetry = versionOf("opentelemetry")
    val openai = versionOf("openai")
    val gemini = versionOf("gemini")
    val anthropic = versionOf("anthropic")
}

dokka {
    val versions = project.getVersions()

    // Configure test fixtures documentation after project evaluation when source sets are created
    project.afterEvaluate {
        val kotlinExt = extensions.findByType(KotlinMultiplatformExtension::class.java)
        val hasTestFixtures = kotlinExt?.sourceSets?.findByName("jvmTestFixtures") != null
        val jvmTestFixturesDir = file("src/jvmTestFixtures/kotlin")

        println("${project.name} hasTestFixtures: $hasTestFixtures")
        println("${project.name} source sets: [${kotlinExt?.sourceSets?.toList()}]")

        if (hasTestFixtures && jvmTestFixturesDir.exists()) {
            dokka.dokkaSourceSets.maybeCreate("jvmTestFixtures").apply {
                displayName.set("JVM Test Fixtures")
                sourceRoots.from(jvmTestFixturesDir)

                suppress.set(false)

                // include classpath for proper type resolution
                classpath.from(
                    configurations.named("jvmCompileClasspath"),
                    configurations.named("jvmTestFixturesCompileClasspath")
                )

                documentedVisibilities.set(setOf(
                    VisibilityModifier.Public,
                    VisibilityModifier.Protected
                ))
            }
        }
    }

    dokkaSourceSets.configureEach {
        includes.from("Module.md")

        pluginsConfiguration.html {
            footerMessage = "Copyright © 2026 JetBrains s.r.o."
        }

        sourceLink {
            localDirectory = rootDir
            remoteUrl("https://github.com/JetBrains/tracy/tree/main")
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

// Documentation archive generation and publishing
val htmlJar = tasks.register<Jar>("dokkaHtmlJar") {
    dependsOn(tasks.dokkaGeneratePublicationHtml)
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

val javadocJar by tasks.register<Jar>("dokkaJavadocJar") {
    dependsOn(tasks.dokkaGeneratePublicationJavadoc)
    from(tasks.dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

publishing {
    publications.withType<MavenPublication> {
        artifact(htmlJar)
    }
}
