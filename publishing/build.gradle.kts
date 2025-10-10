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
            id = "ai.dev.kit.space.publishing"
            implementationClass = "SpacePublishingPlugin"
        }
    }
}
