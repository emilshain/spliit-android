package app.spliit.data.remote

import app.spliit.data.model.ExpenseDocument
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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

/**
 * A failure in this client rather than an answer from the server.
 *
 * [readable] says whether [message] is already written for a person. A message this client
 * composed ("This Spliit server doesn’t accept receipt photos") is; one carrying a platform
 * error, or describing a response that made no sense, is not — the screens show those through
 * [app.spliit.domain.Failures] instead of the text itself.
 */
class TrpcClientException(
    message: String,
    cause: Throwable? = null,
    val readable: Boolean = true,
) : IOException(message, cause)

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
                throw TrpcClientException(
                    "Couldn't decode the response: ${error.message}",
                    error,
                    readable = false,
                )
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

        throw TrpcClientException(
            "Unexpected response ($status): ${body.take(512)}",
            readable = false,
        )
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
                            else TrpcClientException(
                                e.localizedMessage ?: "Network error",
                                e,
                                // The platform's own wording, which names Java classes and
                                // hostnames rather than the connection.
                                readable = false,
                            ),
                        )
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            val body = runCatching { it.body?.string().orEmpty() }
                                .getOrElse { error ->
                                    if (!continuation.isCancelled) {
                                        continuation.resumeWithException(
                                            TrpcClientException(
                                                "Couldn't read the response.",
                                                error,
                                                readable = false,
                                            ),
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

    /**
     * Reads a plain endpoint on the instance — the CSV export, not a tRPC procedure.
     *
     * The instance renders that file itself, so downloading it is how the app gets the same
     * formatting the web app's export button does, without a second implementation to drift.
     */
    suspend fun text(path: String): String {
        val request = Request.Builder().url(plainUrl(path)).get().build()
        val (status, body) = execute(request)
        if (status !in 200..299) {
            throw TrpcClientException("The export could not be fetched ($status).")
        }
        return body
    }

    /**
     * Whether this instance can store receipt photographs at all.
     *
     * Spliit turns expense documents on with `ENABLE_EXPENSE_DOCUMENTS` and an S3 bucket; with
     * either missing, `/api/s3-upload` has no policy to sign for and answers with an error. Asking
     * it for one is the only way a client can tell — the flag is server-side and not part of any
     * tRPC response — so this asks once per process and remembers the answer. No file is sent:
     * the policy is the request's whole answer, and a policy nobody uses stores nothing.
     *
     * A network failure is not a verdict: the server may well accept photos, and hiding the
     * feature because a request timed out would be wrong. Only an answer from the server counts.
     */
    suspend fun supportsDocuments(): Boolean {
        documentsSupported?.let { return it }
        val probe = buildJsonObject {
            put("filename", "receipt-probe.jpg")
            put("contentType", "image/jpeg")
        }
        val request = Request.Builder()
            .url(plainUrl("api/s3-upload"))
            .post(probe.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val supported = try {
            execute(request).first in 200..299
        } catch (error: IOException) {
            return true
        }
        documentsSupported = supported
        return supported
    }

    /** The answer to [supportsDocuments], once the instance has given one. */
    private var documentsSupported: Boolean? = null

    /**
     * Uploads one receipt photograph to the instance and returns the document to attach.
     *
     * Spliit keeps the bytes in the instance's own object storage — S3, or anything S3-compatible
     * — and only the resulting URL in its database, which is what every group member reads back.
     * There is no storage of ours and no key of anyone's: the web app goes through
     * `next-s3-upload`, which asks `/api/s3-upload` for a presigned POST policy and sends the file
     * straight to the bucket. This is the same exchange, so a photo taken here lands exactly where
     * one taken in the browser lands.
     *
     * An instance with expense documents switched off has no bucket to sign for, so the policy
     * request is what fails; that is reported as the feature being unavailable rather than as a
     * network error, because a person can do nothing about the latter.
     */
    suspend fun uploadDocument(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        width: Int,
        height: Int,
    ): ExpenseDocument {
        val policy = buildJsonObject {
            put("filename", fileName)
            put("contentType", mimeType)
        }
        val policyRequest = Request.Builder()
            .url(plainUrl("api/s3-upload"))
            .post(policy.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val (policyStatus, policyBody) = execute(policyRequest)
        if (policyStatus !in 200..299) throw unavailableFor(policyStatus)

        val body = runCatching { Json.parseToJsonElement(policyBody).jsonObject }
            .getOrElse { throw TrpcClientException("The server sent no upload policy.", it) }

        val postUrl = body["url"]?.jsonPrimitive?.contentOrNull
            ?: throw TrpcClientException(UNAVAILABLE)
        val fields = body["fields"]?.jsonObject ?: throw TrpcClientException(UNAVAILABLE)

        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
        for ((name, value) in fields) {
            form.addFormDataPart(name, value.jsonPrimitive.contentOrNull.orEmpty())
        }
        // The policy signs the fields above as they came: nothing may be added or reordered
        // between them and the file, which is why the file goes on last.
        form.addFormDataPart("file", fileName, bytes.toRequestBody(mimeType.toMediaType()))

        val uploadRequest = Request.Builder().url(postUrl).post(form.build()).build()
        val (uploadStatus, uploadBody) = execute(uploadRequest)
        if (uploadStatus !in 200..299) {
            throw TrpcClientException(
                "The receipt couldn’t be stored ($uploadStatus). ${uploadBody.take(200)}".trim(),
            )
        }

        val key = fields["key"]?.jsonPrimitive?.contentOrNull
            ?: throw TrpcClientException("The server stored the photo without a name.")
        return ExpenseDocument(
            id = newDocumentId(),
            url = postUrl.trimEnd('/') + "/" + key,
            width = width,
            height = height,
        )
    }

    private fun unavailableFor(status: Int): TrpcClientException =
        if (status == 404 || status >= 500) TrpcClientException(UNAVAILABLE)
        else TrpcClientException("The receipt couldn’t be uploaded ($status).")

    /** A plain endpoint on the instance, under whatever path it is hosted at. */
    private fun plainUrl(path: String): HttpUrl {
        val builder = validatedBase.newBuilder()
        val prefix = validatedBase.encodedPath.trimEnd('/')
        builder.encodedPath(prefix + "/" + path.trimStart('/'))
        return builder.build()
    }

    /**
     * Spliit's own ids are 24 lowercase characters. The server mints them for a new expense's
     * documents, but an edit connects to a document by id, so one created here needs one too.
     */
    private fun newDocumentId(): String =
        (1..24).map { DOCUMENT_ID_ALPHABET.random() }.joinToString("")

    companion object {
        /** What a server says, in a person's words, when it has no storage to put photos in. */
        const val UNAVAILABLE = "This Spliit server doesn’t accept receipt photos."

        private const val DOCUMENT_ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

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
