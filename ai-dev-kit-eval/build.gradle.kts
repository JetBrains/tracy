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
    testImplementation(libs.kotlin.test)
    testImplementation(testFixtures(project(":ai-dev-kit-test-base")))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
