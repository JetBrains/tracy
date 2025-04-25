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
    implementation(libs.kotlin)
}

kotlin {
    jvmToolchain(17)
}
