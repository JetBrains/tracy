plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("ai.dev.kit.space.publishing")
}

dependencies {
    implementation(libs.kotlin)
    implementation(libs.kotlinx.dataframe)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.opentelemetry.kotlin)
    implementation(libs.kotlin.test)
    implementation(libs.junit)
    implementation(project(":ai-dev-kit-tracing"))
    testImplementation(libs.kotlin.test)
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
