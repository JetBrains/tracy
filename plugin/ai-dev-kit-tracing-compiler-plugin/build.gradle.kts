import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

plugins {
    id("ai.dev.kit.space.publishing")
    id("org.jetbrains.kotlin.multiplatform") version "2.1.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvm {
        compilerOptions.jvmTarget = JVM_17
        withJava()
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
            }
        }
    }
}
