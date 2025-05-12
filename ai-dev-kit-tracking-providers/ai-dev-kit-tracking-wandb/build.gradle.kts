plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("ai.dev.kit.space.publishing")
}

dependencies {
    implementation(libs.kodein)
    implementation(libs.kotlinx.dataframe)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.opentelemetry.sdk)
    implementation(project(":ai-dev-kit-tracing"))
    testImplementation(libs.kotlin.test)
    testImplementation(testFixtures(project(":ai-dev-kit-test-base")))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
