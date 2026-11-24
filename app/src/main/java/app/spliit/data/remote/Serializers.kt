package app.spliit.data.remote

import app.spliit.data.model.ActivityType
import app.spliit.data.model.LenientDecimal
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.Instant

/**
 * ISO-8601 timestamps. The API mixes timestamps with and without milliseconds — `expenseDate`
 * is a bare Postgres `date`, while `createdAt` is a full timestamp — and `Instant.parse` accepts
 * both.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.spliit.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant =
        Instant.parse(decoder.decodeString())
}

/** A `Prisma.Decimal` arrives as a string; a self-hosted instance may send a number. */
object LenientDecimalSerializer : KSerializer<LenientDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.spliit.LenientDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LenientDecimal) {
        encoder.encodeString(value.value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): LenientDecimal {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement()
            ?: return LenientDecimal(BigDecimal(decoder.decodeString()))
        val primitive = element.jsonPrimitive
        return LenientDecimal(
            runCatching { BigDecimal(primitive.content) }.getOrElse {
                error("Expected a decimal as a string or number, found ${primitive.content}.")
            },
        )
    }
}

/**
 * `ActivityType` keeps unknown values rather than failing the whole log, matching the iOS
 * decoder. Only decoding is needed — activities are read-only.
 */
object ActivityTypeSerializer : KSerializer<ActivityType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.spliit.ActivityType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ActivityType) {
        encoder.encodeString(
            when (value) {
                ActivityType.UpdateGroup -> "UPDATE_GROUP"
                ActivityType.CreateExpense -> "CREATE_EXPENSE"
                ActivityType.UpdateExpense -> "UPDATE_EXPENSE"
                ActivityType.DeleteExpense -> "DELETE_EXPENSE"
                is ActivityType.Unknown -> value.raw
            },
        )
    }

    override fun deserialize(decoder: Decoder): ActivityType =
        when (val raw = decoder.decodeString()) {
            "UPDATE_GROUP" -> ActivityType.UpdateGroup
            "CREATE_EXPENSE" -> ActivityType.CreateExpense
            "UPDATE_EXPENSE" -> ActivityType.UpdateExpense
            "DELETE_EXPENSE" -> ActivityType.DeleteExpense
            else -> ActivityType.Unknown(raw)
        }
}
