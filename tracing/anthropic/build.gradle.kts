/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    id("ai.jetbrains.tracy.published-artifact")
    id("ai.kotlin.dokka")
}

kotlin {
    jvmToolchain(17)

    jvm {
        compilerOptions.jvmTarget = JVM_17
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":tracing:core"))
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.anthropic)
                implementation(libs.okhttp)
                implementation(libs.opentelemetry)
                implementation(libs.opentelemetry.semconv.incubating)
                implementation(libs.kotlin.logging)
                implementation(libs.ktor.client)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit.params)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.opentelemetry.sdk.testing)
                implementation(project(":tracing:test-utils"))
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "tracy-$artifactId"
        pom {
            name.set(artifactId)
            description.set("Tracy integration module for Anthropic clients.")
        }
    }
}
