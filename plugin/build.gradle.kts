subprojects {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

tasks.register("publishTracingPlugin") {
    group = "publishing"
    description = "Publishes tracing compiler plugins and gradle plugin"
    val supportedKotlinVersions = listOf("1.9.0", "1.9.20", "2.0.0", "2.0.20", "2.1.0", "2.1.20", "2.2.0", "2.2.20")
    val pluginPublishes = buildList {
        supportedKotlinVersions.forEach { kotlinVersion ->
            add(gradle.includedBuild("ai-dev-kit-tracing-compiler-plugin-$kotlinVersion").task(":publish"))
        }
    }
    dependsOn(pluginPublishes + ":gradle-tracing-plugin:publish")
}