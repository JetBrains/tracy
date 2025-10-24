package ai.dev.kit.trace.gradle

import ai.dev.kit.trace.gradle.AiDevKitTraceGradlePlugin.Companion.PATCH_GROUP_BOUNDARY
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.*

class AiDevKitTraceGradlePlugin : KotlinCompilerPluginSupportPlugin {
    companion object {
        /**
         * Version of the AI Dev Kit plugin.
         *
         * This value is automatically updated by the GitHub Action "Auto Version and Publish Release".
         * Do not rename or remove `VERSION`, or the workflow will fail.
         */
        const val VERSION = "0.0.21"
        const val PATCH_GROUP_BOUNDARY = 20
    }

    private val logger = Logging.getLogger(AiDevKitTraceGradlePlugin::class.java)

    override fun apply(target: Project) {
        super.apply(target)
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val enablePlugin = kotlinCompilation.target.project.findProperty("enableAiDevKitPlugin") as? String
        return enablePlugin?.toBoolean() != false
    }

    override fun getCompilerPluginId(): String = "ai.dev.kit.trace.plugin"

    override fun getPluginArtifact(): SubpluginArtifact {
        val kotlinVersion = getKotlinPluginVersion(logger)
        val pluginVersion = findPluginVersion(kotlinVersion)
        logger.debug("Resolved ai-dev-kit-tracing-compiler-plugin-$pluginVersion for Kotlin compiler $kotlinVersion")
        return SubpluginArtifact(
            groupId = "com.jetbrains",
            artifactId = "ai-dev-kit-tracing-compiler-plugin-$pluginVersion",
            version = VERSION
        )
    }

    override fun getPluginArtifactForNative(): SubpluginArtifact? = null

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        return kotlinCompilation.target.project.provider { emptyList() }
    }
}

/**
 * Normalizes the Kotlin version to align with official compiler plugin build groups.
 *
 * Kotlin compiler plugins are released in grouped versions (e.g., 2.1.0–2.1.19 -> 2.1.0).
 * This normalization ensures that patch releases (e.g., 2.1.11) resolve
 * to the correct compatible plugin artifact (2.1.0 in this case).
 */
private fun findPluginVersion(kotlinVersion: String): String {
    val (major, minor, patch) = kotlinVersion
        .substringBefore('-')
        .split('.')
        .mapNotNull(String::toIntOrNull)
        .let { parts ->
            Triple(
                parts.getOrElse(0) { 0 },
                parts.getOrElse(1) { 0 },
                parts.getOrElse(2) { 0 }
            )
        }
    val normalizedPatch = if (patch >= PATCH_GROUP_BOUNDARY) PATCH_GROUP_BOUNDARY else 0
    return "$major.$minor.$normalizedPatch"
}
