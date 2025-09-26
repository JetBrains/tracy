plugins {
    alias(libs.plugins.kotlin.jvm)
    id("ai.dev.kit.trace")
}

dependencies {
    implementation(libs.kotlinx.coroutines)
    implementation(libs.openai)
    implementation(project(":tracing"))
    implementation(project(":eval"))
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
