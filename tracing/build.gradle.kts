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
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlin)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlin.reflect)

                // TODO GET RID OF BASE EVAL TEST DEPENDENCIES
                implementation(libs.openai)
                implementation(libs.gemini)
                implementation(libs.anthropic)
                implementation(libs.okhttp)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.mock)

                implementation(libs.opentelemetry)
                implementation(libs.opentelemetry.kotlin)
                implementation(libs.opentelemetry.sdk)
                implementation(libs.opentelemetry.exporter.otlp)
                implementation(libs.opentelemetry.exporter.logging)
                implementation(libs.opentelemetry.semconv.incubating)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit.params)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.opentelemetry.sdk.testing)
                implementation(libs.ktor.client.negotiation)
                implementation(libs.ktor.serialization.json)
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}
