package app.application.ports

/**
 * Generic result wrapper for port operations.
 * Encapsulates either a successful value or an error.
 */
data class Result<T>(val data: T? = null, val error: ApiError? = null) {

    fun isSuccess(): Boolean = error == null

    fun getOrThrow(): T {
        if (error != null) {
            throw error.exception ?: RuntimeException(error.message)
        }
        return data!!
    }

    fun onErrorThrow() {
        if (error != null) {
            throw error.exception ?: RuntimeException(error.message)
        }
    }
}

/**
 * Wrapper for API errors.
 */
data class ApiError(
    val message: String = "",
    val exception: Throwable? = null
) {
    constructor(e: Throwable) : this(e.message ?: "", e)
}
