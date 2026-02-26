/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("org.jetbrains.ai.tracy.published-artifact")
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

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(artifactId)
            description.set("Kotlin compiler plugin for enabling Tracy tracing annotations with Kotlin 2.0.0.")
        }
    }
}
