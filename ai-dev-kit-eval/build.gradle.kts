plugins {
    alias(libs.plugins.kotlin.jvm)
    id("ai.dev.kit.space.publishing")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":ai-dev-kit-tracing"))
    implementation(libs.kotlin)
    implementation(libs.kotlinx.dataframe)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.opentelemetry.kotlin)
    implementation(libs.kotlin.test)
    implementation(libs.junit)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
