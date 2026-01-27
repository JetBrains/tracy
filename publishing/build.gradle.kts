plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("commonPublishing") {
            id = "ai.jetbrains.tracy.published-artifact"
            implementationClass = "TracyPublishedArtifactPlugin"
        }
    }
}
