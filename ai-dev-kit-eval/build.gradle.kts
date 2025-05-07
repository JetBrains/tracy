plugins {
    alias(libs.plugins.kotlin.jvm)
    id("ai.dev.kit.space.publishing")
}

dependencies {
    implementation(libs.kotlin)
    implementation(libs.kotlinx.dataframe)
    implementation(project(":ai-dev-kit-core"))
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
