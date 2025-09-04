import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.1.0"
    `maven-publish`
}

group = "com.jetbrains"
version = "1.0.13"

repositories {
    mavenCentral()
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

