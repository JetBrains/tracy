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
         * System properties:
         *  - ai.dev.kit.tracing.capture.input
         *  - ai.dev.kit.tracing.capture.output
         *
         * Environment variables:
         *  - TRACY_CAPTURE_INPUT
         *  - TRACY_CAPTURE_OUTPUT
         */
        fun fromEnvironment(): ContentCapturePolicy {
            fun readBool(sysKey: String, envKey: String, default: Boolean): Boolean {
                val sys = System.getProperty(sysKey)?.trim()
                val env = System.getenv(envKey)?.trim()

                val raw = sys ?: env
                return raw?.let { toBooleanLenient(it) } ?: default
            }

            return ContentCapturePolicy(
                captureInputs = readBool(
                    sysKey = "ai.dev.kit.tracing.capture.input",
                    envKey = "TRACY_CAPTURE_INPUT",
                    default = DEFAULT_CAPTURE_INPUTS,
                ),
                captureOutputs = readBool(
                    sysKey = "ai.dev.kit.tracing.capture.output",
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