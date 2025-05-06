plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("ai.dev.kit.space.publishing")
}

dependencies {
    implementation(project(":ai-dev-kit-core"))
    implementation(libs.kodein)
    implementation(libs.junit)
    implementation(libs.testcontainers.junit)
    implementation(libs.snakeyaml)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.mlflow)
    implementation(libs.opentelemetry)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.kotlin)
    implementation(project(":ai-dev-kit-eval"))
    implementation(libs.kotlinx.dataframe)
    testImplementation(libs.openai)
    testImplementation(libs.kotlin.test)
    testImplementation(testFixtures(project(":ai-dev-kit-test-base")))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
