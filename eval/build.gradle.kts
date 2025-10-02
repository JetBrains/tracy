plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("ai.dev.kit.space.publishing")
}

dependencies {
    implementation(libs.junit)
    implementation(libs.kotlin)
    implementation(libs.kotlin.logging)
    implementation(libs.kotlin.test)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.dataframe)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.opentelemetry.kotlin)
    implementation(libs.opentelemetry.sdk)
    implementation(project(":tracing:tracing-core"))
    runtimeOnly(libs.logback.classic)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
