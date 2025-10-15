plugins {
    `kotlin-dsl`
}

// TODO: See https://slack-chats.kotlinlang.org/t/23147751/i-am-trying-to-change-a-simpel-project-to-a-multi-module-pro

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.dokka.gradle.plugin)
    // implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.0.0")

}