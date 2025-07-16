package ai.dev.kit.eval.utils

fun getUserIDFromEnv(): String =
    System.getenv("USER_ID")
        ?: throw IllegalStateException("USER_ID environment variable is not set")