package com.ead.solargrid.ui.operator

import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import retrofit2.Response

/** Outcome of one staff API call, with a message that is safe to show to the operator. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val code: Int?, val message: String) : ApiResult<Nothing> {
        val isSessionExpired get() = code == OperatorApi.HTTP_UNAUTHORIZED
    }
}

fun <T> ApiResult<T>.dataOr(fallback: T?): T? =
    if (this is ApiResult.Success) data else fallback

object OperatorApi {

    const val HTTP_BAD_REQUEST = 400
    const val HTTP_UNAUTHORIZED = 401
    const val HTTP_FORBIDDEN = 403
    const val HTTP_NOT_FOUND = 404
    const val HTTP_CONFLICT = 409

    const val NETWORK_ERROR = "Can't reach the server. Check your connection and try again."

    suspend fun <T> call(request: suspend () -> Response<T>): ApiResult<T> {
        return try {
            val response = request()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Failure(response.code(), errorMessage(response))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Failure(null, NETWORK_ERROR)
        }
    }

    /** The server's own "message" when it sent one, otherwise a friendly line for the status code. */
    private fun errorMessage(response: Response<*>): String {
        val fromServer = try {
            response.errorBody()?.string()?.let { JSONObject(it).optString("message") }
        } catch (e: Exception) {
            null
        }
        return when {
            !fromServer.isNullOrBlank() -> fromServer
            response.code() == HTTP_UNAUTHORIZED -> "Your session has expired. Please log in again."
            response.code() == HTTP_FORBIDDEN -> "You don't have permission to view this information."
            else -> "Something went wrong (${response.code()}). Please try again."
        }
    }
}
