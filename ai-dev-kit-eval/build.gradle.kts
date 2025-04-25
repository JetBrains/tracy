plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.dataframe)
    id("ai.dev.kit.publish")
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
