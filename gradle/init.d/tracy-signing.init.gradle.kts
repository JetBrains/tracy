import jetbrains.sign.GpgSignSignatoryProvider
import org.gradle.api.publish.PublishingExtension
import org.gradle.plugins.signing.SigningExtension

initscript {
    repositories {
        maven { url = uri("https://packages.jetbrains.team/maven/p/jcs/maven") }
    }
    dependencies {
        classpath("com.jetbrains:jet-sign:45.47")
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