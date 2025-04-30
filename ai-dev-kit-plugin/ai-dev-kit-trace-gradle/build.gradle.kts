import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

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
                password="eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiJhWTliTTA4RFA0USIsImF1ZCI6ImNpcmNsZXQtd2ViLXVpIiwib3JnRG9tYWluIjoiamV0YnJhaW5zIiwibmFtZSI6IlZpYWNoZXNsYXYuU3V2b3JvdiIsImlzcyI6Imh0dHBzOi8vamV0YnJhaW5zLnRlYW0iLCJwZXJtX3Rva2VuIjoiT0tYME0wQWc1R0siLCJwcmluY2lwYWxfdHlwZSI6IlVTRVIiLCJpYXQiOjE3NDQxODUxNzB9.hn2TSJVwEAqEB8du_GHfR56AUCDMJz31Qlry1ZqFwJY4Un9Quh7Dplu8mTo3NMaUO9QkmF6Jqk_5iTxhtfJ7xOz6HdaVLfXIhn2_mAddawpZhMARgtcPJ0T-vqScFUM7fCIhuZUMCVSnwmUDu4E1kU2oBlf97lDRen1FoVk-KEo"
                username="Viacheslav.Suvorov"
//                username = System.getenv("SPACE_USERNAME")
//                password = System.getenv("SPACE_PASSWORD")
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

