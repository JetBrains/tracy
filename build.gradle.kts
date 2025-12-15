plugins {
    id("ai.kotlin.dokka")
    alias(libs.plugins.kotlin.serialization) apply false
}

subprojects {
    repositories {
        mavenCentral()
    }

    tasks.withType<Test> {
        useJUnitPlatform {
            val areTestsRunLocally = System.getProperty("tracyLocalTests", "true").toBoolean()
            if (!areTestsRunLocally) {
                excludeTags("SkipForNonLocal")
            }

            val skipped = System.getProperty("skip.llm.providers")
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList()
            if (skipped.isNotEmpty()) {
                excludeTags(*skipped.toTypedArray())
            }
        }
    }
}

fun registerContentPublishTask(taskName: String, publishType: String, pluginTask: String) {
    tasks.register(taskName) {
        group = "publishing"
        description = "Publishes all modules that apply the ai.jetbrains.tracy.space.publishing plugin ($publishType)"
        val publishTasks = subprojects
            .filter { it.plugins.hasPlugin("ai.jetbrains.tracy.space.publishing") }
            .mapNotNull { it.tasks.findByName(publishType) }
        val pluginPublishTask = gradle.includedBuild("plugin").task(":$pluginTask")
        dependsOn(publishTasks + pluginPublishTask)
    }
}

registerContentPublishTask(
    taskName = "publishContentModules",
    publishType = "publish",
    pluginTask = "publishTracingPlugin"
)
registerContentPublishTask(
    taskName = "publishContentModulesToMavenLocal",
    publishType = "publishToMavenLocal",
    pluginTask = "publishTracingPluginToMavenLocal"
)

dependencies {
    dokka(project(":tracing:anthropic"))
    dokka(project(":tracing:core"))
    dokka(project(":tracing:gemini"))
    dokka(project(":tracing:ktor"))
    dokka(project(":tracing:openai"))
    dokka(project(":tracing:test-utils"))
}

