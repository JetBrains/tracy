plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    id("java-test-fixtures")
}

group = "com.jetbrains"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":ai-dev-kit-core"))
    implementation(libs.ktor.client.jvm)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.openai)
    implementation(libs.okhttp)
    implementation(libs.snakeyaml)
    implementation(libs.opentelemetry)
    implementation(libs.opentelemetry.kotlin)
    implementation(libs.opentelemetry.sdk)
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
kotlin {
    jvmToolchain(17)
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-java-parameters")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
