plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("java-gradle-plugin")
    `maven-publish`
}

group = "com.jetbrains"
version = "1.0.1"

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
        create("aiDevKitTracePlugin") {
            id = "ai.dev.kit.trace"
            implementationClass = "ai.dev.kit.trace.gradle.AiDevKitTraceGradlePlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            url = uri("https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit")
            credentials {
                username = System.getenv("SPACE_USERNAME")
                password = System.getenv("SPACE_PASSWORD")
            }
        }
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

