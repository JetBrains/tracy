plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("ai.dev.kit.space.publishing")
}

dependencies {
    implementation(libs.junit)
    implementation(libs.kotlinx.dataframe)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.mlflow)
    implementation(libs.opentelemetry)
    implementation(libs.opentelemetry.kotlin)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.snakeyaml)
    implementation(libs.testcontainers.junit)
    implementation(project(":ai-dev-kit-tracing"))
    implementation(project(":ai-dev-kit-eval"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.openai)
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.logback.classic)
    testImplementation(testFixtures(project(":ai-dev-kit-test-base")))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
