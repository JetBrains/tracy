plugins {
    `maven-publish`
}

publishing {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        publishing {
            publications {
                create<MavenPublication>("maven") {
                    from(components["kotlin"])
                }
            }
        }
    }

    repositories {
        maven {
            url = uri("https://packages.jetbrains.team/maven/p/ai-development-kit/ai-development-kit")
            credentials {
                username = System.getenv("SPACE_USERNAME")
                password = System.getenv("SPACE_PASSWORD")
            }
        }
    }
}
