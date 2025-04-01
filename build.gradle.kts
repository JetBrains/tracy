plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    kotlin("kapt") version "2.1.20"
}

group = "com.jetbrains"
version = "1.0-SNAPSHOT"
val ktor_version: String by project
val opentelemetry_version: String by project
val logback_version: String by project
val bytebuddy_version: String by project
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
    implementation("io.opentelemetry:opentelemetry-sdk:$opentelemetry_version")
    implementation("io.opentelemetry:opentelemetry-extension-kotlin:$opentelemetry_version")
    implementation("io.opentelemetry:opentelemetry-extension-annotations:1.9.1")
    implementation("io.opentelemetry:opentelemetry-exporter-logging:1.22.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.0")
    implementation("net.bytebuddy:byte-buddy:$bytebuddy_version")
    implementation("net.bytebuddy:byte-buddy-agent:$bytebuddy_version")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.aspectj:aspectjrt:1.9.7")
    implementation("org.aspectj:aspectjweaver:1.9.7")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.yaml:snakeyaml:2.3")
    implementation(kotlin("stdlib"))
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.0")
    implementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainers_version")
    testImplementation("org.testcontainers:testcontainers:$testcontainers_version")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

//tasks.named<JavaExec>("run") {
//    val agentPath = file("/Users/Viacheslav.Suvorov/Downloads/opentelemetry-javaagent.jar").absolutePath
//    jvmArgs("-javaagent:$agentPath")
//}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-java-parameters")
    }
}


