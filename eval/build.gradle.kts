plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.junit)
    implementation(libs.kotlin.logging)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.dataframe)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.opentelemetry.kotlin)
    implementation(libs.opentelemetry.sdk)
    implementation(project(":tracing:core"))
    runtimeOnly(libs.logback.classic)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
