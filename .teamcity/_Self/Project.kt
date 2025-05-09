package _Self

import _Self.buildTypes.Assemble
import _Self.buildTypes.Tests
import _Self.buildTypes.Publish
import _Self.vcsRoots.HttpsGithubComJetBrainsAiDevKitRefsHeadsMain
import jetbrains.buildServer.configs.kotlin.Project

object Project : Project({
    description = "#ai-dev-kit ADM-128054"
    vcsRoot(HttpsGithubComJetBrainsAiDevKitRefsHeadsMain)

    buildType(Assemble)
    buildType(Tests)
    buildType(Publish)
})
