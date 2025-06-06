package ai.dev.kit.tracing.fluent

actual fun getUserIDFromEnv(): String = System.getenv("USER_ID") ?: "unknown-user"