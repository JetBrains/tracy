plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("ai.dev.kit.space.publishing")
}

dependencies {
    implementation(libs.kotlinx.dataframe)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.kotlin)
    implementation(project(":tracing-providers:tracing-providers-core"))
    implementation(project(":ai-dev-kit-eval"))
    testImplementation(libs.kotlin.test)


    // Logging frontend + backend
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
