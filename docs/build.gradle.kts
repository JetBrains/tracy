/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

import java.util.*

group = rootProject.group
version = rootProject.version

plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.knit)
}

kotlin {
    compilerOptions.allWarningsAsErrors.set(true)
}

dependencies {
    implementation(project(":tracing:core"))
    implementation(project(":tracing:anthropic"))
    implementation(project(":tracing:gemini"))
    implementation(project(":tracing:openai"))
    implementation(project(":tracing:ktor"))
    implementation(project(":tracing:test-utils"))
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.exporter.logging)
    implementation(libs.anthropic)
    implementation(libs.gemini)
    implementation(libs.openai)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.ktor.client.cio)
}

val knitProperties: Provider<Properties> =
    providers.fileContents(layout.projectDirectory.file("knit.properties"))
        .asText
        .map { text ->
            Properties().apply {
                text.reader().use { load(it) }
            }
        }

val knitDir: Provider<String> =
    knitProperties.map { props ->
        requireNotNull(props.getProperty("knit.dir")) {
            "Missing 'knit.dir' in knit.properties"
        }
    }


knit {
    rootDir = project.rootDir
    files = fileTree("docs/") {
        include("**/*.md")
    }
    moduleDocs = "docs/modules.md"
    // TODO: add our site link
    siteRoot = "TODO: add site link!"
}

tasks.register<Delete>("knitClean") {
    delete(
        fileTree(project.rootDir) {
            include("**/docs/${knitDir.get()}/**")
        }
    )
}

tasks.named("clean") {
    dependsOn("knitClean")
}

tasks.register<Delete>("knitAssemble") {
    dependsOn("knitClean", "knit", "assemble")
}

tasks.named("knit").configure { mustRunAfter("knitClean") }
tasks.named("assemble").configure { mustRunAfter("knit") }