plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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
        jvmMain {
            dependencies {
                implementation(project(":ai-dev-kit-tracing:ai-dev-kit-tracing-core"))
                implementation(libs.junit)
                implementation(libs.junit.params)
                implementation(libs.kotlin)
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.reflect)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.opentelemetry)
                implementation(libs.opentelemetry.kotlin)
                implementation(libs.opentelemetry.exporter.otlp)
                implementation(libs.opentelemetry.exporter.logging)
                implementation(libs.opentelemetry.semconv.incubating)
                implementation(libs.opentelemetry.sdk)
                implementation(libs.opentelemetry.sdk.testing)
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}