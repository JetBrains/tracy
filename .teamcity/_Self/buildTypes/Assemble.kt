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

    features {
        perfmon {}
        commitStatusPublisher {
            vcsRootExtId = "${HttpsGithubComJetBrainsAiDevKitRefsHeadsMain.id}"
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = personalToken {
                    token = "credentialsJSON:c96b2f3a-a3aa-43ff-8317-5ca8578c21bb"
                }
            }
        }
    }
})
