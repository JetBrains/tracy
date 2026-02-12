/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure

class TracyPublishedArtifactPlugin : Plugin<Project> {
    companion object {
        /**
         * Version of the Tracy plugin.
         *
         * This value is automatically updated by the TeamCity build.
         * Do not rename or remove `VERSION`, or the workflow will fail.
         */
        private const val VERSION = "0.0.26"
    }

    override fun apply(project: Project) {
        project.plugins.apply("maven-publish")

        // Set artifact coordinates here to keep group/version consistent across all
        // publishable Tracy modules and composite builds, avoiding duplication.
        project.group = "org.jetbrains.ai.tracy"
        project.description = "Tracy library for Kotlin for tracing"
        project.version = VERSION

        // NOTE: This is applied ONLY to Kotlin compiler plugin modules.
        // Compiler plugins usually do not produce Dokka Javadoc, but Maven Central
        // still requires a javadoc.jar for every JVM artifact, so we attach an empty one.
        project.afterEvaluate {
            val hasDokkaJavadocJar = tasks.names.contains("dokkaJavadocJar")

            val emptyJavadocJar = if (!hasDokkaJavadocJar) {
                tasks.register("emptyJavadocJar", Jar::class.java) {
                    archiveClassifier.set("javadoc")
                }
            } else null

            extensions.configure<PublishingExtension> {
                publications.withType(MavenPublication::class.java).configureEach {
                    if (!name.contains("jvm")) return@configureEach
                    if (emptyJavadocJar == null) return@configureEach

                    artifact(emptyJavadocJar)
                }
            }
        }
    }
}
