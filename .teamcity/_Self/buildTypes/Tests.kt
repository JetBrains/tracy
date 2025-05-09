package _Self.buildTypes

import _Self.vcsRoots.HttpsGithubComJetBrainsAiDevKitRefsHeadsMain
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.triggers.vcs

object Tests : BuildType( {
    name = "Tests"

    vcs {
        root(HttpsGithubComJetBrainsAiDevKitRefsHeadsMain)
    }

    steps {
        gradle {
            name = "Test Non Local Tests"
            id = "Tests"
            tasks = "test -DaiDevKitLocalTests=false"
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
