plugins {
    alias(libs.plugins.kotlin.jvm)
    id("ai.dev.kit.trace")
}

dependencies {
    implementation(libs.kotlinx.coroutines)
    implementation(libs.openai)
    implementation(project(":ai-dev-kit-tracing"))
    implementation(project(":ai-dev-kit-eval"))
    implementation(project(":ai-dev-kit-tracking-providers:ai-dev-kit-tracking-mlflow"))
    implementation(project(":ai-dev-kit-tracking-providers:ai-dev-kit-tracking-langfuse"))
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
    // enable parallel execution
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    // same thread by default
    systemProperty("junit.jupiter.execution.parallel.mode.default", "same_thread")
}
kotlin {
    jvmToolchain(17)
}
