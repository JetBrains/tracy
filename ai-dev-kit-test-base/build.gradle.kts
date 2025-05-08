plugins {
    alias(libs.plugins.kotlin.jvm)
    id("java-test-fixtures")
    id("ai.dev.kit.trace")
}

dependencies {
    testFixturesImplementation(libs.junit)
    testFixturesImplementation(kotlin("test"))
    testFixturesImplementation(project(":ai-dev-kit-tracing"))
    testFixturesImplementation(libs.kotlinx.coroutines)
    testFixturesImplementation(libs.kotlinx.serialization.json)
    testFixturesImplementation(libs.openai)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-java-parameters")
    }
}

kotlin {
    jvmToolchain(17)
}
