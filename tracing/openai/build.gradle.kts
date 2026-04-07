/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.ai.tracy.published-artifact")
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
                implementation(libs.openai)
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
                implementation(libs.ktor.client)
                implementation(libs.ktor.serialization.json)
                implementation(libs.ktor.client.negotiation)
                implementation(libs.ktor.client.cio)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.opentelemetry.sdk.testing)
                implementation(libs.okhttp.mockwebserver)
                implementation(project.dependencies.testFixtures(project(":tracing:test-utils")))
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xopt-in=org.jetbrains.ai.tracy.core.InternalTracyApi")
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "tracy-$artifactId"
        pom {
            name.set(artifactId)
            description.set("Tracy integration module for OpenAI clients.")
        }
    }
}

// Task to record test fixtures by calling real OpenAI endpoints
tasks.register("recordFixtures") {
    group = "verification"
    description = "Records test fixtures by running tests against real OpenAI endpoints"

    doFirst {
        println("=".repeat(80))
        println("Recording test fixtures from real OpenAI endpoints")
        println("=".repeat(80))
        println("This will:")
        println("  1. Run tests against real LLM provider APIs")
        println("  2. Capture and sanitize responses")
        println("  3. Save fixtures to src/jvmTest/resources/fixtures/")
        println("  4. Re-run tests in mock mode to verify fixtures")
        println()
        println("Make sure ENV variables (e.g., OPENAI_API_KEY) are set in your environment")
        println("=".repeat(80))
        println()
    }

    dependsOn(tasks.withType<Test>().matching {
        it.name.contains("jvmTest") || it.name == "test"
    }.map { testTask ->
        testTask.apply {
            // Set system property to enable RECORD mode
            systemProperty("tracy.test.mode", "record")

            // Filter to run only OpenAI tests
            useJUnitPlatform {
                includeTags("openai")
            }
        }
    })

    doLast {
        println()
        println("=".repeat(80))
        println("Fixtures recorded successfully!")
        println("=".repeat(80))
        println()
        println("Next steps:")
        println("  1. Review the generated fixtures in src/jvmTest/resources/fixtures/")
        println("  2. Run tests in mock mode: ./gradlew :tracing:openai:test")
        println("  3. Commit the fixtures if they look correct")
        println("=".repeat(80))
    }
}

