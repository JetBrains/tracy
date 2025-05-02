import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.triggers.vcs

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2025.03"

project {
    description = "ADM-128054"

    buildType(PublishToSpace)
}

object PublishToSpace : BuildType({
    name = "Publish to space"

    params {
        password("env.SPACE_USERNAME", "credentialsJSON:34cf3ff5-0051-4f0e-9287-aaa408d3c44e")
        password("env.SPACE_PASSWORD", "credentialsJSON:20e578d0-33ec-4600-831b-c27bc27b4be1")
    }

    vcs {
        root(DslContext.settingsRoot)
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
            branchFilter = "+:main"
        }
    }

    features {
        perfmon {
        }
    }
})
