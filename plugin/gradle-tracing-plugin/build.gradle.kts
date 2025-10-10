plugins {
    id("ai.dev.kit.space.publishing")
    id("java-gradle-plugin")
    id("org.jetbrains.kotlin.jvm") version "2.0.0"
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

