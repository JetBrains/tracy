plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group = "com.jetbrains"
version = "1.0-SNAPSHOT"

val space_username: String? by project
val space_password: String? by project

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "ai-dev-kit"
            version = project.version.toString()
            from(components["kotlin"])
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

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvm {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        withJava()
    }

    js(IR) {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlin)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.ktor.client)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.reflect)
                implementation(libs.logback)
                implementation(libs.guice)
                implementation(libs.openai)
                implementation(libs.okhttp)
                implementation(libs.ktor.client.jvm)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.negotiation)
                implementation(libs.ktor.serialization.json)
                implementation(libs.mlflow)
                implementation(libs.opentelemetry)
                implementation(libs.opentelemetry.kotlin)
                implementation(libs.opentelemetry.sdk)
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.junit)
                implementation(libs.snakeyaml)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
            tasks.withType<Test> {
                useJUnitPlatform()
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-java-parameters")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
