import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

plugins {
    id("ai.jetbrains.tracy.space.publishing")
    id("org.jetbrains.kotlin.multiplatform") version "2.1.0"
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

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(artifactId)
            description.set("Kotlin compiler plugin for enabling Tracy tracing annotations with Kotlin 2.1.0.")
        }
    }
}
