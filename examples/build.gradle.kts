/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("org.jetbrains.ai.tracy")
}

dependencies {
    implementation(libs.anthropic)
    implementation(libs.gemini)
    implementation(libs.koog)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.openai)
    implementation(libs.opentelemetry.kotlin)
    implementation(project(":eval"))
    implementation(project(":tracing:anthropic"))
    implementation(project(":tracing:core"))
    implementation(project(":tracing:gemini"))
    implementation(project(":tracing:ktor"))
    implementation(project(":tracing:openai"))
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-java-parameters"
        )
    }
}

kotlin {
    jvmToolchain(17)
}
