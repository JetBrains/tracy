plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false

}
group = "com.jetbrains"
version = "1.0.1"

subprojects {
    group = rootProject.group
    version = rootProject.version
    repositories {
        mavenCentral()
    }
}

tasks.register("publishContentModules") {
    group = "publishing"
    description = "Publishes all modules that apply the ai.dev.kit.publish plugin. All important modules except plugin"
    val publishTasks = subprojects.filter { subproject ->
        subproject.plugins.hasPlugin("ai.dev.kit.publish")
    }.mapNotNull { subproject ->
        subproject.tasks.findByName("publish")
    }
    dependsOn(publishTasks)
}
