import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

plugins {
    id("ai.jetbrains.tracy.space.publishing")
    id("org.jetbrains.kotlin.multiplatform") version "2.1.20"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
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
