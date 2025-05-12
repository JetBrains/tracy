package _Self.vcsRoots

import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

object HttpsGithubComJetBrainsAiDevKitRefsHeadsMain : GitVcsRoot({
    name = "https://github.com/JetBrains/ai-dev-kit#refs/heads/main"
    url = "https://github.com/JetBrains/ai-dev-kit"
    branch = "refs/heads/main"
    branchSpec = "+:refs/heads/*"
    authMethod = password {
        userName = "slawa4s"
        password = "credentialsJSON:560e7223-211b-49a8-9452-7d03dd7324a5"
    }
})
