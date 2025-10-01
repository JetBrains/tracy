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
                implementation(project(":tracing:tracing-core"))
                implementation(libs.kotlin)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.kotlin.reflect)
                implementation(libs.ktor.client)
                implementation(libs.ktor.client.cio)
                implementation(libs.okhttp)
                implementation(libs.opentelemetry)
                implementation(libs.opentelemetry.kotlin)
                implementation(libs.opentelemetry.sdk)
                implementation(libs.opentelemetry.semconv.incubating)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit.params)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.client.negotiation)
                implementation(libs.ktor.serialization.json)
                implementation(libs.opentelemetry.sdk.testing)
                implementation(project(":tracing:tracing-test-utils"))
                implementation(project(":tracing:tracing-core"))
                implementation(project(":tracing:tracing-openai"))
                implementation(project(":tracing:tracing-anthropic"))
                implementation(project(":tracing:tracing-gemini"))

            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}
