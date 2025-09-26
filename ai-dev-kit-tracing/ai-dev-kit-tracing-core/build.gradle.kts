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
        // Enable test fixtures for JVM
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }

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
                implementation(libs.kotlin)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.kotlin.reflect)
                implementation(libs.opentelemetry)
                implementation(libs.opentelemetry.kotlin)
                implementation(libs.opentelemetry.sdk)
                implementation(libs.opentelemetry.exporter.otlp)
                implementation(libs.opentelemetry.exporter.logging)
                implementation(libs.opentelemetry.semconv.incubating)
                implementation(libs.okhttp)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit.params)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.opentelemetry.sdk.testing)
                implementation(project(":ai-dev-kit-tracing:ai-dev-kit-tracing-test-utils"))
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-java-parameters")
    }
}

kotlin {
    jvmToolchain(17)
}