import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar

class SpacePublishingPlugin : Plugin<Project> {
    companion object {
        private const val VERSION = "1.0.20"
        private const val PROJECT_PREFIX = "ai-dev-kit-"
    }

    override fun apply(project: Project) {
        project.plugins.apply("maven-publish")

        project.group = "com.jetbrains"
        project.version = VERSION

        project.extensions.configure<PublishingExtension>("publishing") {
            repositories {
                maven {
                    url = project.uri("https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit")
                    credentials {
                        username = System.getenv("SPACE_USERNAME")
                        password = System.getenv("SPACE_PASSWORD")
                    }
                }
            }
        }

        project.afterEvaluate {
            project.extensions.configure<PublishingExtension>("publishing") {
                if (project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                    publications.withType(MavenPublication::class.java).all {
                        if (!artifactId.startsWith(PROJECT_PREFIX)) {
                            artifactId = PROJECT_PREFIX + artifactId
                            project.logger.lifecycle("Renamed KMP publication to: $artifactId")
                        }
                    }
                } else {
                    publications.withType(MavenPublication::class.java).matching { it.name == "pluginMaven" }.all {
                        if (!artifactId.startsWith(PROJECT_PREFIX)) {
                            artifactId = PROJECT_PREFIX + artifactId
                            project.logger.lifecycle("Renamed pluginMaven artifactId to: $artifactId")
                        }

                        // attach sources JAR
                        val sourcesJar = project.tasks.register("sourcesJar", Jar::class.java) {
                            archiveClassifier.set("sources")
                            from(
                                project.extensions
                                    .findByName("sourceSets")
                                    ?.let { it as SourceSetContainer }
                                    ?.getByName("main")
                                    ?.allSource
                            )
                        }
                        artifact(sourcesJar.get())
                    }
                }
            }
        }
    }
}
