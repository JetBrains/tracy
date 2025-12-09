package ai.dev.kit.tracing.policy

/**
 * Controls whether to capture sensitive LLM content in spans.
 * By default, all sensitive content capture is disabled per OTEL guidance.
 */
data class ContentCapturePolicy(
    val captureInputs: Boolean = DEFAULT_CAPTURE_INPUTS,
    val captureOutputs: Boolean = DEFAULT_CAPTURE_OUTPUTS,
) {
    companion object {
        const val DEFAULT_CAPTURE_INPUTS: Boolean = false
        const val DEFAULT_CAPTURE_OUTPUTS: Boolean = false

        /**
         * Creates a policy using system properties or environment variables when present.
         * Precedence: system properties > env vars > defaults.
         *
         * Environment variables:
         *  - TRACY_CAPTURE_INPUT
         *  - TRACY_CAPTURE_OUTPUT
         */
        fun fromEnvironment(): ContentCapturePolicy {
            fun readBool(envKey: String, default: Boolean): Boolean {
                val env = System.getenv(envKey)?.trim()
                return env?.let { toBooleanLenient(it) } ?: default
            }

            return ContentCapturePolicy(
                captureInputs = readBool(
                    envKey = "TRACY_CAPTURE_INPUT",
                    default = DEFAULT_CAPTURE_INPUTS,
                ),
                captureOutputs = readBool(
                    envKey = "TRACY_CAPTURE_OUTPUT",
                    default = DEFAULT_CAPTURE_OUTPUTS,
                ),
            )
        }

        private fun toBooleanLenient(value: String): Boolean =
            when (value.lowercase()) {
                "true", "1", "yes", "y", "on" -> true
                "false", "0", "no", "n", "off" -> false
                else -> false
            }
    }
}