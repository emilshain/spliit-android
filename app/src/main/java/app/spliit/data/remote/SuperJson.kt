package app.spliit.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Encoding and decoding for the superjson envelope the Spliit API wraps every payload in.
 *
 * superjson sends `{"json": <value>, "meta": {"values": …}}`, where `meta.values` maps
 * dot-separated key paths to annotations for values plain JSON cannot express — `Date`.
 *
 * Decoding deliberately ignores `meta.values`. The models are statically typed, so a field the
 * server annotated as a `Date` is already an [java.time.Instant] here and parses straight from
 * its ISO-8601 string. Encoding *does* emit annotations, so the server rebuilds real `Date`
 * instances before its own validation runs.
 */
object SuperJson {

    /**
     * `explicitNulls = false` matches Swift's synthesised `encodeIfPresent`: a nil optional is
     * dropped rather than sent as `null`, which the server reads as `undefined` and Prisma
     * treats as "leave the column alone". [Requests.expenseFormValuesJson] re-adds the one
     * field that must be sent as an explicit null.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = false
    }

    /** Keys whose values are calendar days rather than moments; see [app.spliit.domain.CalendarDay]. */
    private val calendarDayKeys = setOf("expenseDate")

    /** Wraps [value] in a superjson envelope, annotating every calendar day it contains. */
    fun envelope(value: JsonElement): JsonObject {
        val datePaths = mutableListOf<String>()
        collectDatePaths(value, emptyList(), datePaths)

        return buildJsonObject {
            put("json", value)
            if (datePaths.isNotEmpty()) {
                put(
                    "meta",
                    buildJsonObject {
                        put(
                            "values",
                            JsonObject(
                                datePaths.associateWith {
                                    JsonArray(listOf(JsonPrimitive("Date")))
                                },
                            ),
                        )
                    },
                )
            }
        }
    }

    fun encodeToString(value: JsonElement): String = envelope(value).toString()

    fun <T> decode(deserializer: KSerializer<T>, body: String): T =
        json.decodeFromString(TrpcSuccess.serializer(deserializer), body).result.data.json

    /** Reads `{"error":{"json": …}}`, or null if the body isn't a tRPC error. */
    fun decodeFailure(body: String): TrpcErrorBodyJson? =
        runCatching { json.decodeFromString(TrpcFailure.serializer(), body).error.json }.getOrNull()

    private fun collectDatePaths(
        value: JsonElement,
        path: List<String>,
        into: MutableList<String>,
    ) {
        when (value) {
            is JsonObject -> value.forEach { (key, nested) ->
                collectDatePaths(nested, path + key, into)
            }

            is JsonArray -> value.forEachIndexed { index, nested ->
                collectDatePaths(nested, path + index.toString(), into)
            }

            else -> if (path.isNotEmpty() && path.last() in calendarDayKeys) {
                into += path.joinToString(".")
            }
        }
    }

    @Serializable
    internal data class TrpcSuccess<T>(val result: TrpcResult<T>)

    @Serializable
    internal data class TrpcResult<T>(val data: TrpcData<T>)

    @Serializable
    internal data class TrpcData<T>(val json: T)

    @Serializable
    internal data class TrpcFailure(val error: TrpcErrorBody)

    @Serializable
    internal data class TrpcErrorBody(val json: TrpcErrorBodyJson)

    @Serializable
    data class TrpcErrorBodyJson(
        val message: String,
        val data: TrpcErrorDetails? = null,
    )

    @Serializable
    data class TrpcErrorDetails(
        val code: String? = null,
        val httpStatus: Int? = null,
        val path: String? = null,
    )
}

/**
 * Output for procedures that return nothing meaningful — `groups.update` returns `undefined`,
 * `expenses.delete` returns `{}`. Accepts either shape.
 */
object TrpcVoidSerializer : KSerializer<Unit> {
    override val descriptor = buildClassSerialDescriptor("app.spliit.TrpcVoid")

    override fun serialize(encoder: Encoder, value: Unit) {
        (encoder as JsonEncoder).encodeJsonElement(JsonNull)
    }

    override fun deserialize(decoder: Decoder): Unit {
        (decoder as JsonDecoder).decodeJsonElement()
    }
}
