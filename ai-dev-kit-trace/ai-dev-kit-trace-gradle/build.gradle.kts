plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("java-gradle-plugin")
}

group = "ai.dev.kit"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(kotlin("gradle-plugin"))
    implementation(kotlin("gradle-plugin-api"))
}

gradlePlugin {
    plugins {
        create("aiDevKitTrace") {
            id = "ai.dev.kit.trace"
            implementationClass = "ai.dev.kit.trace.gradle.AiDevKitTraceGradlePlugin"
        }
    }
}
