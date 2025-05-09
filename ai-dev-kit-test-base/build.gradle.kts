plugins {
    alias(libs.plugins.kotlin.jvm)
    id("ai.dev.kit.trace")
    id("java-test-fixtures")
}

dependencies {
    testFixturesImplementation(kotlin("test"))
    testFixturesImplementation(libs.junit)
    testFixturesImplementation(libs.kotlinx.coroutines)
    testFixturesImplementation(libs.kotlinx.serialization.json)
    testFixturesImplementation(libs.openai)
    testFixturesImplementation(project(":ai-dev-kit-core"))
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
