plugins {
    alias(libs.plugins.kotlin.jvm)
    id("ai.dev.kit.trace")
}

dependencies {
    implementation(libs.kotlinx.coroutines)
    implementation(libs.openai)
    implementation(project(":ai-dev-kit-tracing"))
    implementation(project(":ai-dev-kit-eval"))
    implementation(project(":ai-dev-kit-tracking-providers:ai-dev-kit-tracking-langfuse"))
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
