package _Self.vcsRoots

import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

object HttpsGithubComJetBrainsAiDevKitRefsHeadsMain : GitVcsRoot({
    name = "https://github.com/JetBrains/ai-dev-kit#refs/heads/main"
    url = "https://github.com/JetBrains/ai-dev-kit"
    branch = "refs/heads/main"
    branchSpec = "+:refs/heads/*"
    authMethod = password {
        userName = "slawa4s"
        password = "credentialsJSON:203056e3-5166-4dda-89a4-f5669dbcad7b"
    }
})
