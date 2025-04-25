plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlinx.dataframe)
}

group = "com.jetbrains"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":ai-dev-kit-core"))
    implementation(libs.kotlin)
    implementation(libs.kotlinx.dataframe)
}

kotlin {
    jvmToolchain(17)
}
