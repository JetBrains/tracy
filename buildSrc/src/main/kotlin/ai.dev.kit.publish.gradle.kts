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
                username = "Viacheslav.Suvorov"//System.getenv("SPACE_USERNAME")
                password = "eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiJhWTliTTA4RFA0USIsImF1ZCI6ImNpcmNsZXQtd2ViLXVpIiwib3JnRG9tYWluIjoiamV0YnJhaW5zIiwibmFtZSI6IlZpYWNoZXNsYXYuU3V2b3JvdiIsImlzcyI6Imh0dHBzOi8vamV0YnJhaW5zLnRlYW0iLCJwZXJtX3Rva2VuIjoiM1BLOGt6MnFhZ1A0IiwicHJpbmNpcGFsX3R5cGUiOiJVU0VSIiwiaWF0IjoxNzQ2MTkxNDc1fQ.BArOb-DeMOvGj251n0AnV2NnaDqC1CQVcMcaYMeZ4rYYvTXbATD37Vd8o5oRRgfFC_O8oTzG6D9vQSqfFYKr1cjg3oTEkonsltj5bztvBv_p6w1OfsQuI0nUNo1ipujuHpWmk8Nv_Ddm1IpgwPpILL-u2MBH7EG-CtGHHfxK4TA"//System.getenv("SPACE_PASSWORD")
            }
        }
    }
}
