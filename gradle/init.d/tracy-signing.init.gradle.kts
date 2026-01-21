import jetbrains.sign.GpgSignSignatoryProvider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

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
    plugins.withId("ai.jetbrains.tracy.space.publishing") {
        println(">>> Signing enabled for project $path")
        val isUnderTeamCity = System.getenv("TEAMCITY_VERSION") != null
        plugins.apply("signing")
        extensions.configure<SigningExtension> {
            if (isUnderTeamCity) {
                val publishing = extensions.getByType(PublishingExtension::class.java)
                sign(publishing.publications)
                signatories = GpgSignSignatoryProvider()
            } else {
                isRequired = false
            }
        }

        afterEvaluate {
            extensions.configure<PublishingExtension> {
                repositories.maven {
                    name = "artifacts"
                    url = uri(layout.buildDirectory.dir("artifacts/maven"))
                }

                publications.register("maven", MavenPublication::class.java) {
                    artifactId = "tracy-${project.name}"
                    groupId = project.group.toString()
                    version = project.version.toString()

                    from(components["kotlin"])

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
        tasks.register("packSonatypeCentralBundle", Zip::class.java) {
            group = "publishing"

            dependsOn(":publishAllToArtifacts")

            subprojects
                .forEach { sub ->
                    from(sub.layout.buildDirectory.dir("artifacts/maven"))
                }

            from(projectDir.resolve("plugin")) {
                include("**/build/artifacts/maven/**")
            }

            archiveFileName.set("bundle.zip")
            destinationDirectory.set(layout.buildDirectory)
        }

        tasks.register("publishMavenToCentralPortal") {
            group = "publishing"

            dependsOn("packSonatypeCentralBundle")

            doLast {
                val uriBase = "https://central.sonatype.com/api/v1/publisher/upload"
                val publishingType = "USER_MANAGED"
                val deploymentName = "${project.name}-${project.version}"
                val uri = "$uriBase?name=$deploymentName&publishingType=$publishingType"

                val userName = rootProject.extra["centralPortalUserName"] as String
                val token = rootProject.extra["centralPortalToken"] as String
                val base64Auth = Base64.getEncoder()
                    .encode("$userName:$token".toByteArray())
                    .toString(Charsets.UTF_8)

                val bundleTask = tasks.named("packSonatypeCentralBundle", Zip::class.java).get()
                val bundleFile = bundleTask.archiveFile.get().asFile

                println("Sending request to $uri...")

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(uri)
                    .header("Authorization", "Bearer $base64Auth")
                    .post(
                        MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("bundle", bundleFile.name, bundleFile.asRequestBody())
                            .build()
                    )
                    .build()

                client.newCall(request).execute().use { response ->
                    val statusCode = response.code
                    val bodyText = response.body?.string().orEmpty()
                    println("Upload status code: $statusCode")
                    println("Upload result: $bodyText")
                    if (statusCode != 201) {
                        error("Upload error to Central repository. Status code $statusCode.")
                    }
                }
            }
        }
    }
}
