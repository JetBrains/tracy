import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    id("ai.jetbrains.tracy.space.publishing")
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
                implementation(libs.kotlin.reflect)
                implementation(libs.ktor.client)
                implementation(libs.ktor.client.cio)
                implementation(libs.okhttp)
                implementation(libs.opentelemetry)
                implementation(libs.opentelemetry.kotlin)
                implementation(libs.opentelemetry.sdk)
                implementation(libs.opentelemetry.semconv.incubating)
                implementation(libs.kotlin.logging)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit.params)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.client.negotiation)
                implementation(libs.ktor.serialization.json)
                implementation(libs.opentelemetry.sdk.testing)
                implementation(project(":tracing:test-utils"))
                implementation(project(":tracing:openai"))
                implementation(project(":tracing:anthropic"))
                implementation(project(":tracing:gemini"))
                implementation(libs.openai)
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "tracy-$artifactId"
        pom {
            name.set(artifactId)
            description.set("Tracy integration module for Ktor HTTP clients.")
        }
    }
}

