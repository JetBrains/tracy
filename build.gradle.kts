plugins {
    id("ai.kotlin.dokka")
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
    dokka(project(":tracing:tracing-anthropic"))
    dokka(project(":tracing:tracing-openai"))
    dokka(project(":tracing:tracing-gemini"))
    dokka(project(":tracing:tracing-ktor"))
    dokka(project(":tracing:tracing-test-utils"))
    dokka(project(":tracing:tracing-core"))
}

