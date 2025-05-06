package ai.dev.kit.core.fluent

actual fun getUserIDFromEnv(): String =
    System.getenv("USER_ID")
        ?: throw IllegalStateException("USER_ID environment variable is not set")