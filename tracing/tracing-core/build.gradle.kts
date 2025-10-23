import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    id("ai.dev.kit.space.publishing")
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
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.okhttp)
                implementation(libs.ktor.client)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.serialization.json)
                implementation(libs.ktor.client.negotiation)
                implementation(libs.kotlin.reflect)
                implementation(libs.opentelemetry.sdk)
                implementation(libs.opentelemetry.kotlin)
                implementation(libs.opentelemetry.exporter.otlp)
                implementation(libs.opentelemetry.exporter.logging)
                implementation(libs.opentelemetry.semconv.incubating)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit.params)
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":tracing:tracing-test-utils"))
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-java-parameters", "-Xexpect-actual-classes"
        )
    }
}
