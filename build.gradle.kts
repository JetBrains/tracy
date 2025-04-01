plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "com.jetbrains"
version = "1.0-SNAPSHOT"
val ktor_version: String by project
val opentelemetry_version: String by project
val logback_version: String by project
val mlflow_client_version: String by project
val testcontainers_version: String by project

repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("com.google.inject:guice:7.0.0")
    implementation("com.openai:openai-java:0.34.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("org.mlflow:mlflow-client:$mlflow_client_version")
    implementation("io.opentelemetry:opentelemetry-api:$opentelemetry_version")
    implementation("io.opentelemetry:opentelemetry-extension-kotlin:$opentelemetry_version")
    implementation("io.opentelemetry:opentelemetry-sdk:$opentelemetry_version")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.yaml:snakeyaml:2.3")
    implementation(kotlin("test"))
    implementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainers_version")
    testImplementation("org.testcontainers:testcontainers:$testcontainers_version")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-java-parameters")
    }
}
