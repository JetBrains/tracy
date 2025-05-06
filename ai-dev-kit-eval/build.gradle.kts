plugins {
    alias(libs.plugins.kotlin.jvm)
    id("ai.dev.kit.space.publishing")
}

dependencies {
    implementation(project(":ai-dev-kit-core"))
    implementation(libs.kotlin)
    implementation(libs.kotlinx.dataframe)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
