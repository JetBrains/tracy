import java.util.Properties

group = rootProject.group
version = rootProject.version

plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.knit)
     id("ai.kotlin.dokka")
}

kotlin {
    compilerOptions.allWarningsAsErrors.set(true)
}

dependencies {
    implementation(project(":tracing:tracing-core"))
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.exporter.logging)
}


dokka {
    dokkaSourceSets.configureEach {
        suppress.set(true)
    }
}

val knitProperties: Provider<Properties> =
    providers.fileContents(layout.projectDirectory.file("knit.properties"))
        .asText
        .map { text ->
            Properties().apply {
                text.reader().use { load(it) }
            }
        }

val knitDir: Provider<String> =
    knitProperties.map { props ->
        requireNotNull(props.getProperty("knit.dir")) {
            "Missing 'knit.dir' in knit.properties"
        }
    }

//ktlint {
//    filter {
//        exclude { it.file.path.contains("/docs/${knitDir.get()}/") }
//    }
//}

knit {
    rootDir = project.rootDir
    files = fileTree("docs/") {
        include("**/*.md")
    }
    moduleDocs = "docs/modules.md"
    siteRoot = "https://docs.koog.ai/"
}

tasks.register<Delete>("knitClean") {
    delete(
        fileTree(project.rootDir) {
            include("**/docs/${knitDir.get()}/**")
        }
    )
}

tasks.named("clean") {
    dependsOn("knitClean")
}

tasks.register<Delete>("knitAssemble") {
    dependsOn("knitClean", "knit", "assemble")
}

tasks.named("knit").configure { mustRunAfter("knitClean") }
tasks.named("assemble").configure { mustRunAfter("knit") }