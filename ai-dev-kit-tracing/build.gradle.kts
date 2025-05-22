plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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
                implementation(libs.kodein)

                // TODO GET RID OF BASE EVAL TEST DEPENDENCIES
                implementation(libs.openai)
                implementation(libs.okhttp)
                implementation(libs.opentelemetry)
                implementation(libs.opentelemetry.kotlin)
                implementation(libs.opentelemetry.sdk)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}