/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

subprojects {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

val supportedKotlinVersions =
    listOf("1.9.0", "1.9.20", "2.0.0", "2.0.20", "2.1.0", "2.1.20", "2.2.0", "2.2.20", "2.3.0")

fun registerTracingPublishTask(taskName: String, publishTaskName: String) {
    tasks.register(taskName) {
        group = "publishing"
        description = "Publishes tracing compiler plugins and gradle plugin ($publishTaskName)"
        val compilerPluginPublishes = supportedKotlinVersions.map { kotlinVersion ->
            gradle.includedBuild("tracy-compiler-plugin-$kotlinVersion").task(":$publishTaskName")
        }
        dependsOn(compilerPluginPublishes + ":gradle-tracy-plugin:$publishTaskName")
    }
}

registerTracingPublishTask(
    taskName = "publishTracingPluginToSpace",
    publishTaskName = "publishAllPublicationsToSpaceRepository"
)

registerTracingPublishTask(
    taskName = "publishTracingPluginToMavenLocal",
    publishTaskName = "publishToMavenLocal"
)

registerTracingPublishTask(
    taskName = "publishTracingPluginToArtifactsRepository",
    publishTaskName = "publishAllPublicationsToArtifactsRepository"
)

