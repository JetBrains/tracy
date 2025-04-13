plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
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
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
