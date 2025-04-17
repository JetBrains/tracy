plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
}

group = "ai.dev.kit"
version = "1.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
}
