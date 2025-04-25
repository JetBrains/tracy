plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.1.0"
}

group = "ai.dev.kit"
version = "1.0.1"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
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
