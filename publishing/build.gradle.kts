/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("commonPublishing") {
            id = "org.jetbrains.ai.tracy.published-artifact"
            implementationClass = "TracyPublishedArtifactPlugin"
        }
    }
}
