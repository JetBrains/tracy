plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("ai.dev.kit.trace")
    id("ai.dev.kit.space.publishing")
}

kotlin {
    jvm {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        withJava()
    }

    js(IR) {
        browser()
    }

    sourceSets.all {
        compilerOptions {
            freeCompilerArgs.add("-Xexpected-actual-classes")
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":ai-dev-kit-tracing:ai-dev-kit-tracing-core"))
                implementation(libs.kotlin)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.kotlin.reflect)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.mock)
                implementation(libs.opentelemetry.sdk)
                implementation(libs.opentelemetry.semconv.incubating)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit.params)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.negotiation)
                implementation(libs.ktor.serialization.json)
                implementation(libs.opentelemetry.sdk.testing)
                implementation(libs.opentelemetry.semconv.incubating)
                implementation(project(":ai-dev-kit-tracing:ai-dev-kit-tracing-openai"))
                implementation(project(":ai-dev-kit-tracing:ai-dev-kit-tracing-anthropic"))
                implementation(project(":ai-dev-kit-tracing:ai-dev-kit-tracing-gemini"))
                implementation(project(":ai-dev-kit-tracing:ai-dev-kit-tracing-test-utils"))
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}