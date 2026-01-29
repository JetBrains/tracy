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
        val jvmTestFixtures by getting {
            dependencies {
                implementation(project(":tracing:core"))
                implementation(libs.opentelemetry.sdk.testing)
                implementation(libs.ktor.client)
                implementation(libs.kotlin.test)
                implementation(libs.junit)
            }
        }
    }
}
