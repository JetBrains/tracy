plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false

}
group = "com.jetbrains"
version = "1.0.17-junie"

subprojects {
    group = rootProject.group
    version = rootProject.version
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
        "Publishes all modules that apply the ai.dev.kit.space.publishing plugin. All important modules except plugin"
    val publishTasks = subprojects.filter { subproject ->
        subproject.plugins.hasPlugin("ai.dev.kit.space.publishing")
    }.mapNotNull { subproject ->
        subproject.tasks.findByName("publish")
    }
    dependsOn(publishTasks)
}
