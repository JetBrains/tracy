/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("org.jetbrains.ai.tracy.published-artifact")
    id("java-gradle-plugin")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
}

dependencies {
    implementation(kotlin("gradle-plugin"))
    implementation(kotlin("gradle-plugin-api"))
}

gradlePlugin {
    plugins {
        create("TracyPublishingPlugin") {
            id = "org.jetbrains.ai.tracy"
            implementationClass = "org.jetbrains.ai.tracy.gradle.plugin.TracyGradlePlugin"
        }
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

java {
    withSourcesJar()
    withJavadocJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

afterEvaluate {
    extensions.configure<PublishingExtension> {
        publications.withType<MavenPublication>().configureEach {
            groupId = "org.jetbrains.ai.tracy"
            artifactId = "org.jetbrains.ai.tracy.gradle.plugin"
            pom {
                name.set(artifactId)
                description.set("Gradle plugin for configuring Tracy annotation based tracing in Kotlin projects.")
            }
        }
    }
}
