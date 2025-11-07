import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    id("ai.dev.kit.trace")
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
                implementation(project(":tracing:tracing-core"))
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.gemini)
                implementation(libs.okhttp)
                implementation(libs.opentelemetry)
                implementation(libs.opentelemetry.kotlin)
                implementation(libs.opentelemetry.sdk)
                implementation(libs.opentelemetry.sdk.testing)
                implementation(libs.opentelemetry.semconv.incubating)
                implementation(libs.opentelemetry.exporter.otlp)
                implementation(libs.opentelemetry.exporter.logging)
                implementation(libs.kotlin.test)
                implementation(libs.junit)
                implementation(libs.junit.params)
                implementation(libs.kotlinx.coroutines)
            }
        }
    }
}
