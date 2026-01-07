plugins {
    id("ai.jetbrains.tracy.space.publishing")
    id("org.jetbrains.kotlin.multiplatform") version "2.0.0"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
            }
        }
    }
}
