package ai.dev.kit.trace.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.*

class AiDevKitTraceGradlePlugin : KotlinCompilerPluginSupportPlugin {
    companion object {
        const val VERSION = "1.0.18"
    }

    override fun apply(target: Project) {
        super.apply(target)
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val enablePlugin = kotlinCompilation.target.project.findProperty("enableAiDevKitPlugin") as? String
        return enablePlugin?.toBoolean() != false
    }

    override fun getCompilerPluginId(): String = "ai.dev.kit.trace.plugin"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "com.jetbrains",
        artifactId = "ai-dev-kit-tracing-compiler-plugin",
        version = VERSION
    )

    override fun getPluginArtifactForNative(): SubpluginArtifact? = null

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        return kotlinCompilation.target.project.provider { emptyList() }
    }
}
