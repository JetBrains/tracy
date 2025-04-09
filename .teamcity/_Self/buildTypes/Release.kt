package _Self.buildTypes

import _Self.vcsRoots.HttpsGithubComJetBrainsAiDevKitRefsHeadsMain
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildFeatures.swabra
import jetbrains.buildServer.configs.kotlin.buildFeatures.vcsLabeling
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle

object Release : BuildType({
    name = "Release"

    params {
        password("env.SPACE_USERNAME", "credentialsJSON:a4570abb-2f7a-4a92-9ffe-fc1478a579de")
        password("env.SPACE_PASSWORD", "credentialsJSON:a4570abb-2f7a-4a92-9ffe-fc1478a579de")
    }

    vcs {
        root(HttpsGithubComJetBrainsAiDevKitRefsHeadsMain)
    }

    steps {
        gradle {
            name = "Upload to maven repo"
            id = "Upload to maven repo"
            tasks = ":publish"
            jdkHome = "%env.JDK_17_0%"
            gradleParams = "-PpublicationRepositoryUrl=${IntelliJDependenciesPackageBot.mavenRepoUrl} " +
                    "-PpublicationUsername=${USERNAME_PARAM.ref()} " +
                    "-PpublicationPassword=${PASSWORD_PARAM.ref()} " +
                    "--info"
            enableStacktrace = true
        }
    }
})
