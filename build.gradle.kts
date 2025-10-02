plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
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
    val pluginPublishes = listOf(
        gradle.includedBuild("plugin").task(":ai-dev-kit-tracing-compiler-plugin:publish"),
        gradle.includedBuild("plugin").task(":gradle-tracing-plugin:publish")
    )

    dependsOn(publishTasks + pluginPublishes)
}
