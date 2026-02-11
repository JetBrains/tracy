/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */


import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import jetbrains.sign.GpgSignSignatoryProvider
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

initscript {
    repositories {
        maven { url = uri("https://packages.jetbrains.team/maven/p/jcs/maven") }
        mavenCentral()
    }
    dependencies {
        classpath("com.jetbrains:jet-sign:45.47")
        classpath("com.squareup.okhttp3:okhttp:4.12.0")
    }
}

allprojects {
    plugins.withId("ai.jetbrains.tracy.published-artifact") {
        val isUnderTeamCity = System.getenv("TEAMCITY_VERSION") != null
        val isSigningRequired = System.getenv("IS_SIGNING_REQUIRED")?.toBoolean() ?: false
        plugins.apply("signing")
        extensions.configure<SigningExtension> {
            if (isUnderTeamCity && isSigningRequired) {
                val publishing = extensions.getByType(PublishingExtension::class.java)
                sign(publishing.publications)
                signatories = GpgSignSignatoryProvider()
            } else {
                isRequired = false
            }
        }

        afterEvaluate {
            val signAllPublications = tasks.register("signAllPublications") {
                group = "signing"
                description = "Signs all Maven publications in this project"
                dependsOn(
                    tasks.matching { it.name.startsWith("sign") && it.name.endsWith("Publication") })
            }

            tasks.matching { it.name.endsWith("PublicationToArtifactsRepository") }.configureEach {
                dependsOn(signAllPublications)
            }

            extensions.configure<PublishingExtension> {
                repositories.maven {
                    name = "artifacts"
                    url = uri(layout.buildDirectory.dir("artifacts/maven"))
                }

                repositories.maven {
                    name = "space"
                    url = project.uri("https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit")
                    credentials {
                        username = System.getenv("SPACE_USERNAME")
                        password = System.getenv("SPACE_PASSWORD")
                    }
                }

                publications.withType(MavenPublication::class.java).configureEach {
                    groupId = project.group.toString()
                    version = project.version.toString()

                    pom {
                        url.set("https://github.com/JetBrains/tracy")

                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                            }
                        }

                        developers {
                            developer {
                                id.set("anton.bragin")
                                name.set("Anton Bragin")
                                email.set("anton.bragin@jetbrains.com")
                                organization.set("JetBrains")
                                organizationUrl.set("https://www.jetbrains.com")
                            }
                            developer {
                                id.set("anastasia.maltseva")
                                name.set("Anastasia Maltseva")
                                email.set("anastasia.maltseva@jetbrains.com")
                                organization.set("JetBrains")
                                organizationUrl.set("https://www.jetbrains.com")
                            }
                            developer {
                                id.set("georgii.zorabov")
                                name.set("Georgii Zorabov")
                                email.set("georgii.zorabov@jetbrains.com")
                                organization.set("JetBrains")
                                organizationUrl.set("https://www.jetbrains.com")
                            }
                            developer {
                                id.set("viacheslav.suvorov")
                                name.set("Viacheslav Suvorov")
                                email.set("viacheslav.suvorov@jetbrains.com")
                                organization.set("JetBrains")
                                organizationUrl.set("https://www.jetbrains.com")
                            }
                            developer {
                                id.set("vladislav.artiukhov")
                                name.set("Vladislav Artiukhov")
                                email.set("vladislav.artiukhov@jetbrains.com")
                                organization.set("JetBrains")
                                organizationUrl.set("https://www.jetbrains.com")
                            }
                        }

                        scm {
                            connection.set("scm:git:https://github.com/JetBrains/tracy.git")
                            url.set("https://github.com/JetBrains/tracy")
                        }
                    }
                }
            }
        }
    }
}

gradle.rootProject {
    afterEvaluate {
        fun base64Auth(userName: String, accessToken: String): String =
            Base64.getEncoder().encode("$userName:$accessToken".toByteArray()).toString(Charsets.UTF_8)

        fun deployToCentralPortal(
            bundleFile: java.io.File,
            uriBase: String,
            isUserManaged: Boolean,
            deploymentName: String,
            userName: String,
            accessToken: String
        ): String {
            val publishingType = if (isUserManaged) "USER_MANAGED" else "AUTOMATIC"
            val uri = uriBase.trimEnd('/') +
                    "/api/v1/publisher/upload?name=$deploymentName&publishingType=$publishingType"
            val authHeader = base64Auth(userName, accessToken)

            println("Sending request to $uri...")

            val client = OkHttpClient()
            val request = Request.Builder()
                .url(uri)
                .header("Authorization", "Bearer $authHeader")
                .post(
                    MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("bundle", bundleFile.name, bundleFile.asRequestBody())
                        .build()
                )
                .build()

            client.newCall(request).execute().use { response ->
                val statusCode = response.code
                println("Upload status code: $statusCode")
                val uploadResult = response.body?.string().orEmpty()
                println("Upload result: $uploadResult")

                if (statusCode == 201) {
                    return uploadResult.trim()
                } else {
                    error("Upload error to Central repository. Status code $statusCode. Body: $uploadResult")
                }
            }
        }

        fun waitForUploadToSucceed(
            uriBase: String,
            deploymentId: String,
            isUserManaged: Boolean,
            userName: String,
            accessToken: String,
            maxTimeout: Duration,
            minTimeBetweenAttempts: Duration
        ) {
            val uri = uriBase.trimEnd('/') + "/api/v1/publisher/status?id=$deploymentId"
            val authHeader = base64Auth(userName, accessToken)

            var timeSpent = Duration.ZERO
            var attemptNumber = 1
            var terminatingState = false

            println("Polling for deployment status for $maxTimeout: $uri")

            while (timeSpent < maxTimeout) {
                val remainingTime = maxTimeout - timeSpent
                println("Polling attempt ${attemptNumber++}, remaining time $remainingTime.")

                val client = OkHttpClient().newBuilder()
                    .callTimeout(remainingTime.toJavaDuration())
                    .build()

                val beforeMs = System.currentTimeMillis()
                try {
                    val request = Request.Builder()
                        .url(uri)
                        .header("Authorization", "Bearer $authHeader")
                        .post("".toRequestBody())
                        .build()

                    client.newCall(request).execute().use { response ->
                        val code = response.code
                        if (code != 200) {
                            error("Response code $code: ${response.body?.string()}")
                        }

                        val bytes = response.body?.bytes() ?: error("Empty response body.")
                        val jsonResult = JsonSlurper().parse(bytes) as Map<*, *>

                        val state = jsonResult["deploymentState"]
                        println("Current state: $state.")

                        when (state) {
                            "PENDING", "VALIDATING", "PUBLISHING" -> {
                                // keep polling
                            }

                            "VALIDATED" -> {
                                terminatingState = true
                                if (isUserManaged) {
                                    println("Deployment validated successfully; waiting for manual publish in Portal UI.")
                                    return
                                }
                                error("State error: deployment is not user managed, but signals it requires a UI interaction.")
                            }

                            "PUBLISHED" -> {
                                terminatingState = true
                                if (!isUserManaged) {
                                    println("Deployment published successfully to Maven Central.")
                                    return
                                }
                                error("State error: deployment is user managed, but signals it has been published.")
                            }

                            "FAILED" -> {
                                terminatingState = true
                                val errors = jsonResult["errors"]
                                val errorsAsString = JsonBuilder(errors).toPrettyString()
                                error("Deployment failed. Errors: $errorsAsString")
                            }

                            else -> logger.warn("Unknown deployment state: $state")
                        }
                    }
                } catch (e: Exception) {
                    if (terminatingState) {
                        throw e
                    }
                    logger.warn("Error during HTTP request: ${e.message}")
                } finally {
                    val afterMs = System.currentTimeMillis()
                    var attemptTime = (afterMs - beforeMs).coerceAtLeast(0L).milliseconds
                    if (attemptTime < minTimeBetweenAttempts) {
                        val sleepTime = minTimeBetweenAttempts - attemptTime
                        Thread.sleep(sleepTime.inWholeMilliseconds)
                        attemptTime = minTimeBetweenAttempts
                    }
                    timeSpent += attemptTime
                }
            }

            error("Timed out while waiting for deployment $deploymentId to finish.")
        }

        tasks.register("packSonatypeCentralBundle", Zip::class.java) {
            group = "publishing"

            dependsOn(":publishAllToArtifacts")

            subprojects.forEach { sub ->
                from(sub.layout.buildDirectory.dir("artifacts/maven"))
            }

            from(projectDir.resolve("plugin")) {
                include("**/build/artifacts/maven/**")
                eachFile {
                    val marker = "build/artifacts/maven/"
                    val idx = path.indexOf(marker)
                    if (idx >= 0) {
                        path = path.substring(idx + marker.length)
                    }
                }
                includeEmptyDirs = false
            }

            archiveFileName.set("bundle.zip")
            destinationDirectory.set(layout.buildDirectory)
        }

        tasks.register("publishMavenToCentralPortal") {
            group = "publishing"

            dependsOn("packSonatypeCentralBundle")

            doLast {
                val uriBase = rootProject.extra["centralPortalUrl"] as String
                val userName = rootProject.extra["centralPortalUserName"] as String
                val token = rootProject.extra["centralPortalToken"] as String

                val publishingTypeEnv = System.getenv("SONATYPE_PUBLISHING_TYPE")?.uppercase()
                val isUserManaged = when (publishingTypeEnv) {
                    "AUTOMATIC" -> false
                    else -> true
                }
                val buildNumber = System.getenv("BUILD_NUMBER") ?: "local"
                val deploymentName = "${project.name}-build-$buildNumber"

                val bundleTask = tasks.named("packSonatypeCentralBundle", Zip::class.java).get()
                val bundleFile = bundleTask.archiveFile.get().asFile

                val deploymentId = deployToCentralPortal(
                    bundleFile = bundleFile,
                    uriBase = uriBase,
                    isUserManaged = isUserManaged,
                    deploymentName = deploymentName,
                    userName = userName,
                    accessToken = token
                )

                waitForUploadToSucceed(
                    uriBase = uriBase,
                    deploymentId = deploymentId,
                    isUserManaged = isUserManaged,
                    userName = userName,
                    accessToken = token,
                    maxTimeout = 60.minutes,
                    minTimeBetweenAttempts = 5.seconds
                )
            }
        }
    }
}