import org.gradle.api.Plugin
import org.gradle.api.Project

class SpacePublishingPlugin : Plugin<Project> {
    companion object {
        /**
         * Version of the Tracy plugin.
         *
         * This value is automatically updated by the TeamCity build.
         * Do not rename or remove `VERSION`, or the workflow will fail.
         */
        private const val VERSION = "0.0.24"
    }

    override fun apply(project: Project) {
        project.plugins.apply("maven-publish")

        project.group = "ai.jetbrains.tracy"
        project.description = "Tracy library for Kotlin for tracing"
        project.version = VERSION
    }
}
