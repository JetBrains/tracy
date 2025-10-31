import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("ai.dev.kit.trace")
}

dependencies {
    implementation(libs.anthropic)
    implementation(libs.gemini)
    implementation(libs.koog)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.openai)
    implementation(libs.opentelemetry.kotlin)
    implementation(project(":eval"))
    implementation(project(":tracing:tracing-anthropic"))
    implementation(project(":tracing:tracing-core"))
    implementation(project(":tracing:tracing-gemini"))
    implementation(project(":tracing:tracing-ktor"))
    implementation(project(":tracing:tracing-openai"))
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
