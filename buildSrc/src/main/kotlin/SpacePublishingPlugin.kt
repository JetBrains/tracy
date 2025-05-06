import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar

class SpacePublishingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("maven-publish")

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
            if (project.plugins.hasPlugin("org.jetbrains.kotlin.jvm")) {
                val sourcesJar = project.tasks.register("sourcesJar", Jar::class.java) {
                    archiveClassifier.set("sources")
                    from(project.extensions.getByName("sourceSets")
                        .let { it as SourceSetContainer }
                        .getByName("main").allSource
                    )
                }
                publications {
                    create("maven", MavenPublication::class.java) {
                        from(project.components.findByName("kotlin"))
                        artifact(sourcesJar.get())
                    }
                }
            }
        }
    }
}
