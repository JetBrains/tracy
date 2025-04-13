plugins {
    kotlin("jvm")
}

group = "com.jetbrains"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":ai-dev-kit-core"))
    implementation(project(":ai-dev-kit-tracking-providers:ai-dev-kit-tracking-mlflow"))
    implementation(libs.openai)
    implementation(libs.kotlinx.coroutines)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
