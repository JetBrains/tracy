plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false

}
group = "com.jetbrains"
version = "1.0.4"

subprojects {
    group = rootProject.group
    version = rootProject.version
    repositories {
        mavenCentral()
    }
    tasks.withType<Test> {
        useJUnitPlatform {
            if (System.getProperty("aiDevKitLocalTests", "true").toBoolean()) {
                includeTags("SkipForNonLocal")
            } else {
                excludeTags("SkipForNonLocal")
            }
        }
    }
}

tasks.register("showCreds") {
    description = "Displays credentials for debugging purposes"
    doLast {
        val username = System.getenv("SPACE_USERNAME") ?: "Not Set"
        val password = System.getenv("SPACE_PASSWORD") ?: "Not Set"

        println("SPACE_USERNAME: $username")
        println("SPACE_PASSWORD: $password")
    }
}


tasks.register("publishContentModules") {
    group = "publishing"
    description = "Publishes all modules that apply the ai.dev.kit.space.publishing plugin. All important modules except plugin"
    val publishTasks = subprojects.filter { subproject ->
        subproject.plugins.hasPlugin("ai.dev.kit.space.publishing")
    }.mapNotNull { subproject ->
        subproject.tasks.findByName("publish")
    }
    dependsOn(publishTasks)
}
