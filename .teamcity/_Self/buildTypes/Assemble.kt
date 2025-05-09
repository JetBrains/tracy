package _Self.buildTypes

import _Self.vcsRoots.HttpsGithubComJetBrainsAiDevKitRefsHeadsMain
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.buildSteps.maven

object Assemble : BuildType( {
    name = "Assemble"

    vcs {
        root(HttpsGithubComJetBrainsAiDevKitRefsHeadsMain)
    }

    steps {
        gradle {
            name = "Assemble"
            id = "Assemble"
            tasks = "assemble"
        }
        maven {
            name = "Generate TeamCity DSL"
            id = "Generate_TeamCity_DSL"
            workingDir = ".teamcity"
            pomLocation = ".teamcity/pom.xml"
            goals = "clean teamcity-configs:generate"
        }
    }

    triggers {
        vcs {
            branchFilter = """
                +:<default>
                +:pull/*
            """.trimIndent()
        }
    }

    features {
        perfmon {}
        commitStatusPublisher {
            vcsRootExtId = "${HttpsGithubComJetBrainsAiDevKitRefsHeadsMain.id}"
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = vcsRoot()
            }
        }
    }
})
