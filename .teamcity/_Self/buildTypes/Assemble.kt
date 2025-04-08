package _Self.buildTypes

import _Self.vcsRoots.HttpsGithubComJetBrainsAiDevKitRefsHeadsMain
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.buildSteps.maven

object Assemble : BuildType({
    name = "Assemble"
    paused = true

    vcs {
        root(HttpsGithubComJetBrainsAiDevKitRefsHeadsMain)
    }

    steps {
        gradle {
            name = "Assemble"
            id = "Assemble"
            tasks = "assemble"
            jdkHome = "%env.JDK_17_0%"
        }

        maven {
            name = "Generate TeamCity DSL"
            id = "Generate_TeamCity_DSL"
            workingDir = ".teamcity"
            pomLocation = ".teamcity/pom.xml"
            goals = "clean teamcity-configs:generate"
            jdkHome = "%env.JDK_17_0%"
        }
    }
})
