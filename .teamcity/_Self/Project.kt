package _Self

import _Self.buildTypes.Assemble
import _Self.buildTypes.Release
import _Self.vcsRoots.HttpsGithubComJetBrainsAiDevKitRefsHeadsMain
import jetbrains.buildServer.configs.kotlin.Project


object Project : Project({
    description = "#ai-dev-kit"

    vcsRoot(HttpsGithubComJetBrainsAiDevKitRefsHeadsMain)

    buildType(Assemble)
    buildType(Release)
})
