plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    `maven-publish`
}

group = "com.jetbrains"
version = "1.0-SNAPSHOT"

val ktor_version: String by project
val opentelemetry_version: String by project
val logback_version: String by project
val mlflow_client_version: String by project
val testcontainers_version: String by project

val space_username: String? by project
val space_password: String? by project

repositories {
    mavenCentral()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "ai-dev-kit"
            version = project.version.toString()
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri("https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit")
            credentials {
                username = System.getenv("SPACE_USERNAME") ?: space_username ?: error("Environment variable 'SPACE_USERNAME' is not set.")
                password = System.getenv("SPACE_PASSWORD") ?: space_password ?: error("Environment variable 'SPACE_PASSWORD' is not set.")
            }
        }
    }
}

dependencies {
    implementation("ch.qos.logback:logback-classic:$logback_version")
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
    implementation("io.opentelemetry:opentelemetry-extension-kotlin:$opentelemetry_version")
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
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-java-parameters")
    }
}
