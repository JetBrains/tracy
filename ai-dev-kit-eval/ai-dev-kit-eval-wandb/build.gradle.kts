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
    implementation(project(":ai-dev-kit-eval:ai-dev-kit-eval-base"))
    testImplementation(testFixtures(project(":ai-dev-kit-eval:ai-dev-kit-eval-base")))
    implementation(libs.junit)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.opentelemetry)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.kotlin)
    testImplementation(libs.openai)
    testImplementation(libs.kotlin.test)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-java-parameters")
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}