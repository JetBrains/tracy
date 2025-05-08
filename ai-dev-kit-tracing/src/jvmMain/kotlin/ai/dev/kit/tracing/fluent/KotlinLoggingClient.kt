package ai.dev.kit.tracing.fluent

actual fun getUserIDFromEnv(): String =
    System.getenv("USER_ID")
        ?: throw IllegalStateException("USER_ID environment variable is not set")