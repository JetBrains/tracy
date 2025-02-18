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

repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("com.google.inject:guice:7.0.0")
    implementation("com.openai:openai-java:0.21.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("org.mlflow:mlflow-client:$mlflow_client_version")
    implementation("io.opentelemetry:opentelemetry-api:$opentelemetry_version")
    implementation("io.opentelemetry:opentelemetry-sdk:$opentelemetry_version")
    implementation("io.opentelemetry:opentelemetry-exporter-logging:$opentelemetry_version")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.10")
    implementation("org.yaml:snakeyaml:2.3")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}