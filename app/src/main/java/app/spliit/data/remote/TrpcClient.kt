package app.spliit.data.remote

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One call to the Spliit API: where the procedure lives and whether it reads or writes. */
data class TrpcProcedure<T> internal constructor(
    val path: String,
    val kind: Kind,
    val input: JsonElement?,
    val deserializer: kotlinx.serialization.KSerializer<T>,
) {
    enum class Kind { QUERY, MUTATION }
}

/** A tRPC error the server described, e.g. `NOT_FOUND` for a procedure an instance lacks. */
class TrpcServerException(
    val code: String?,
    message: String,
    val httpStatus: Int,
    val path: String?,
) : IOException(message) {
    /** An instance that predates the procedure — a feature to degrade, not a crash. */
    val isUnknownProcedure: Boolean get() = code == "NOT_FOUND"
}

class TrpcClientException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Talks to a Spliit instance's tRPC endpoint.
 *
 * Requests are sent unbatched: queries as `GET …/api/trpc/<path>?input=<envelope>`, mutations
 * as `POST` with the envelope as the body. The server accepts both, so there is no need to
 * implement tRPC's batching protocol.
 */
class TrpcClient(
    baseUrl: String,
    private val callFactory: Call.Factory = defaultCallFactory,
) {
    /** The instance root, with any trailing slash removed. */
    val baseUrl: String = baseUrl.trimEnd('/')

    private val validatedBase: HttpUrl =
        baseUrl.trimEnd('/').toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Not an absolute http(s) URL: $baseUrl")

    suspend fun <T> call(procedure: TrpcProcedure<T>): T {
        val request = buildRequest(procedure)
        val (status, body) = execute(request)

        if (status in 200..299) {
            return try {
                SuperJson.decode(procedure.deserializer, body)
            } catch (error: Exception) {
                throw TrpcClientException("Couldn't decode the response: ${error.message}", error)
            }
        }

        val failure = SuperJson.decodeFailure(body)
        if (failure != null) {
            throw TrpcServerException(
                code = failure.data?.code,
                message = failure.message,
                httpStatus = failure.data?.httpStatus ?: status,
                path = failure.data?.path,
            )
        }

        throw TrpcClientException("Unexpected response ($status): ${body.take(512)}")
    }

    private fun buildRequest(procedure: TrpcProcedure<*>): Request {
        val builder = validatedBase.newBuilder()
        // Preserve any path prefix the instance is hosted under, then append the procedure.
        val prefix = validatedBase.encodedPath.trimEnd('/')
        builder.encodedPath(prefix + "/api/trpc/" + procedure.path)

        return when (procedure.kind) {
            TrpcProcedure.Kind.QUERY -> {
                if (procedure.input != null) {
                    builder.addQueryParameter("input", procedure.input.toString())
                }
                Request.Builder()
                    .url(builder.build())
                    .get()
                    .header("Accept", "application/json")
                    .build()
            }

            TrpcProcedure.Kind.MUTATION -> Request.Builder()
                .url(builder.build())
                .post(
                    (procedure.input?.toString() ?: "{}")
                        .toRequestBody("application/json".toMediaType()),
                )
                .header("Accept", "application/json")
                .build()
        }
    }

    private suspend fun execute(request: Request): Pair<Int, String> =
        suspendCancellableCoroutine { continuation ->
            val call = callFactory.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isCancelled) return
                        continuation.resumeWithException(
                            if (e is TrpcServerException) e
                            else TrpcClientException(e.localizedMessage ?: "Network error", e),
                        )
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            val body = runCatching { it.body?.string().orEmpty() }
                                .getOrElse { error ->
                                    if (!continuation.isCancelled) {
                                        continuation.resumeWithException(
                                            TrpcClientException("Couldn't read the response.", error),
                                        )
                                    }
                                    return
                                }
                            if (!continuation.isCancelled) {
                                continuation.resume(it.code to body)
                            }
                        }
                    }
                },
            )
        }

    companion object {
        /**
         * `OkHttpClient` default is 10 seconds, which a person reads as the app having hung.
         * Failing at 20 lets the screen offer a retry while the attempt is still remembered.
         */
        val defaultCallFactory: Call.Factory = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
