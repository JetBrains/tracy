import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

plugins {
    id("ai.dev.kit.space.publishing")
    id("org.jetbrains.kotlin.multiplatform") version "2.1.20"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvm {
        compilerOptions.jvmTarget = JVM_17
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
            }
        }
    }
}
