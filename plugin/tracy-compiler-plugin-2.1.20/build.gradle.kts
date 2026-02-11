/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

plugins {
    id("ai.jetbrains.tracy.published-artifact")
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

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(artifactId)
            description.set("Kotlin compiler plugin for enabling Tracy tracing annotations with Kotlin 2.1.20.")
        }
    }
}
