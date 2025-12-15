subprojects {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

val supportedKotlinVersions = listOf("1.9.0", "1.9.20", "2.0.0", "2.0.20", "2.1.0", "2.1.20", "2.2.0", "2.2.20")

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
    taskName = "publishTracingPlugin",
    publishTaskName = "publish"
)

registerTracingPublishTask(
    taskName = "publishTracingPluginToMavenLocal",
    publishTaskName = "publishToMavenLocal"
)
