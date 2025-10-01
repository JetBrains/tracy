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
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit.params)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}
