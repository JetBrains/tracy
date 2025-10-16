import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("ai.dev.kit.trace")
}

dependencies {
    implementation(libs.koog)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.openai)
    implementation(libs.gemini)
    implementation(libs.anthropic)
    implementation(project(":eval"))
    implementation(project(":tracing:tracing-core"))
    implementation(project(":tracing:tracing-openai"))
    implementation(project(":tracing:tracing-gemini"))
    implementation(project(":tracing:tracing-anthropic"))
    implementation(project(":tracing:tracing-ktor"))
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-java-parameters"
        )
    }
}

kotlin {
    jvmToolchain(17)
}
