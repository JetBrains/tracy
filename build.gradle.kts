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

dependencies {
    dokka(project(":tracing:anthropic"))
    dokka(project(":tracing:core"))
    dokka(project(":tracing:gemini"))
    dokka(project(":tracing:ktor"))
    dokka(project(":tracing:openai"))
    dokka(project(":tracing:test-utils"))
}

