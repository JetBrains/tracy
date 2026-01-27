plugins {
    id("ai.jetbrains.tracy.published-artifact")
    id("java-gradle-plugin")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
}

dependencies {
    implementation(kotlin("gradle-plugin"))
    implementation(kotlin("gradle-plugin-api"))
}

gradlePlugin {
    plugins {
        create("TracyPublishingPlugin") {
            id = "ai.jetbrains.tracy"
            implementationClass = "ai.jetbrains.tracy.gradle.plugin.TracyGradlePlugin"
        }
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

java {
    withSourcesJar()
    withJavadocJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(artifactId)
            description.set("Gradle plugin for configuring Tracy annotation based tracing in Kotlin projects.")
        }
    }
}
