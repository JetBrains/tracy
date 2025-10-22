plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    id("ai.dev.kit.trace") apply false
}

subprojects {
    repositories {
        mavenCentral()
    }
    tasks.withType<Test> {
        useJUnitPlatform {
            val areTestsRunLocally = System.getProperty("aiDevKitLocalTests", "true").toBoolean()
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

tasks.register("publishContentModules") {
    group = "publishing"
    description =
        "Publishes all modules that apply the ai.dev.kit.space.publishing plugin"
    val publishTasks = subprojects.filter { subproject ->
        subproject.plugins.hasPlugin("ai.dev.kit.space.publishing")
    }.mapNotNull { subproject ->
        subproject.tasks.findByName("publish")
    }
    val pluginPublishTasks = gradle.includedBuild("plugin").task(":publishTracingPlugin")
    dependsOn(publishTasks + pluginPublishTasks)
}
