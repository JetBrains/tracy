package ai.dev.kit.common

/**
 * Describes the result of an arbitrary operation, either an operation's result or an error.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: Int? = null) : Result<Nothing>()

    fun isSuccess() = this is Success

    val value: T
        get() = when (this) {
            is Success<T> -> this.data
            is Error -> error("Cannot cast the result to success when it's an error")
        }

    val error: Error
        get() = when (this) {
            is Success<*> -> error("Cannot cast the successful result to an error")
            is Error -> this
        }
}