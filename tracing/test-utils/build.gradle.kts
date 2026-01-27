/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    id("ai.kotlin.dokka")
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(17)

    jvm {
        compilerOptions.jvmTarget = JVM_17

        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        commonMain {
            dependencies {
//                implementation(project(":tracing:core"))
//                implementation(libs.kotlinx.serialization.core)
//                implementation(libs.kotlinx.serialization.json)
            }
        }

        jvmMain {
            dependencies {
//                implementation(libs.gemini)
//                implementation(libs.okhttp)
//                implementation(libs.opentelemetry)
//                implementation(libs.opentelemetry.kotlin)
//                implementation(libs.opentelemetry.sdk)
//                implementation(libs.opentelemetry.sdk.testing)
//                implementation(libs.opentelemetry.semconv.incubating)
//                implementation(libs.opentelemetry.exporter.otlp)
//                implementation(libs.opentelemetry.exporter.logging)
//                implementation(libs.kotlin.test)
//                implementation(libs.junit)
//                implementation(libs.junit.params)
//                implementation(libs.kotlinx.coroutines)
//                implementation(libs.ktor.client)
            }
        }

        val jvmTestFixtures by getting {
            dependencies {
                // TODO: needed explicitly?
                // implementation(kotlin("stdlib"))

                implementation(project(":tracing:core"))
                implementation(libs.opentelemetry.sdk.testing)
                implementation(libs.ktor.client)
                implementation(libs.kotlin.test)
                implementation(libs.junit)
            }
        }
    }
}
