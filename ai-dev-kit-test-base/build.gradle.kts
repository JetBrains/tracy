plugins {
    kotlin("jvm")
    id("java-test-fixtures")
}

group = "com.jetbrains"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testFixturesImplementation(libs.junit)
    testFixturesImplementation(kotlin("test"))
    testFixturesImplementation(project(":ai-dev-kit-core"))
    testFixturesImplementation(libs.kotlinx.coroutines)
    testFixturesImplementation(libs.kotlinx.serialization.json)
    testFixturesImplementation(libs.openai)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-java-parameters")
    }
}

kotlin {
    jvmToolchain(17)
}