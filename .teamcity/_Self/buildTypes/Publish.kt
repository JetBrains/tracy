package _Self.buildTypes;

import _Self.vcsRoots.HttpsGithubComJetBrainsAiDevKitRefsHeadsMain
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.triggers.vcs

object Publish : BuildType({
    name = "Publish to space"
    paused = true
    type = Type.DEPLOYMENT
    maxRunningBuilds = 1

    vcs {
        root(HttpsGithubComJetBrainsAiDevKitRefsHeadsMain)
    }

    params {
        password("env.SPACE_USERNAME", "credentialsJSON:cac278db-1591-4ad6-9fc4-4ecef5f5e853")
        password("env.SPACE_PASSWORD", "credentialsJSON:1440f937-9ef2-49d7-9811-ade3dbe778a9")
    }

    steps {
        gradle {
            name = "Publish space"
            id = "Publish_space"
            tasks = "ai-dev-kit-trace-gradle:publish ai-dev-kit-trace-plugin:publish :publishContentModules"
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
    }
})

