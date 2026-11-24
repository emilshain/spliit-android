package app.spliit.data.local

import app.spliit.data.model.Participant
import app.spliit.data.model.SplitMode
import app.spliit.data.remote.NullableInstantSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/**
 * Who the person holding this phone is, in one group.
 *
 * Spliit has no accounts, so this is a local answer to a local question, and it is asked once
 * per group: someone can be Ana in the flatshare and a guest on the ski trip.
 *
 * Three states, which is why this is not simply a `String?`. A group nobody has answered for
 * should be asked; a group answered with "nobody" — a shared phone, a group kept for other
 * people — should not be asked again. `null` is the question, [Nobody] is an answer to it.
 */
sealed interface ActiveParticipant {
    data class Participant(val id: String) : ActiveParticipant

    data object Nobody : ActiveParticipant

    val participantId: String?
        get() = when (this) {
            is Participant -> id
            Nobody -> null
        }

    companion object {
        /**
         * Reads a stored answer against the group it was stored for.
         *
         * A participant who has since been removed reads as *unanswered* rather than as a
         * missing person: the alternative is a screen that shows no balance, names nobody, and
         * offers no way to say so.
         */
        // Qualified: inside this interface, `Participant` means the nested case below.
        fun resolve(
            stored: ActiveParticipant?,
            participants: List<app.spliit.data.model.Participant>,
        ): ActiveParticipant? =
            when (stored) {
                is Participant -> if (participants.any { it.id == stored.id }) stored else null
                Nobody -> Nobody
                null -> null
            }
    }
}

/**
 * Encoded as the bare participant ID, and as an empty string for "nobody", so the stored list
 * stays something a person can read and a test can write by hand.
 */
object ActiveParticipantSerializer : KSerializer<ActiveParticipant?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.spliit.ActiveParticipant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ActiveParticipant?) {
        val jsonEncoder = encoder as? JsonEncoder
        when (value) {
            null -> if (jsonEncoder != null) {
                jsonEncoder.encodeJsonElement(JsonNull)
            } else {
                encoder.encodeString("")
            }

            is ActiveParticipant.Participant -> encoder.encodeString(value.id)
            ActiveParticipant.Nobody -> encoder.encodeString("")
        }
    }

    override fun deserialize(decoder: Decoder): ActiveParticipant? {
        val jsonDecoder = decoder as? JsonDecoder
        val raw = if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            if (element is JsonNull) return null
            element.jsonPrimitive.content
        } else {
            decoder.decodeString()
        }
        return if (raw.isEmpty()) ActiveParticipant.Nobody else ActiveParticipant.Participant(raw)
    }
}

/**
 * How a group's expenses are usually divided, remembered so the next one starts there.
 *
 * It does not travel to the server: the flag the web app sends alongside an expense is read by
 * its own browser and by nothing else, so a default set here is one this app remembers.
 */
@Serializable
data class DefaultSplit(
    val splitMode: SplitMode,
    /**
     * Everyone in the remembered split, and what each was given, on the ×100 scale the protocol
     * uses for share counts and percentages alike.
     *
     * Nil for [SplitMode.BY_AMOUNT], whose shares are one expense's own amounts — the €12.40
     * somebody owed on Tuesday's receipt means nothing on Wednesday's.
     */
    val shares: Map<String, Int>? = null,
) {
    /**
     * Whether this still describes a split of [participants].
     *
     * Somebody remembered who has since left the group makes the whole of it stale rather than
     * smaller: the shares that are left no longer add up to what anyone agreed — 70/30 without
     * the 30 is not "70", it is a percentage split that cannot be saved — and quietly dropping a
     * name is how a default becomes a split nobody chose. A new participant is not a problem:
     * they were never in it.
     */
    fun appliesTo(participants: List<Participant>): Boolean {
        val shares = this.shares ?: return true
        val present = participants.mapTo(HashSet()) { it.id }
        return shares.keys.all { it in present }
    }
}

/**
 * A group the user has opened, remembered locally. Spliit has no accounts — this list is the
 * only way back to a group.
 *
 * The property names match the JSON the React Native app wrote, so the same decoder reads both
 * the legacy value and ours.
 */
@Serializable
data class RecentGroup(
    val groupId: String,
    val groupName: String,
    /**
     * Kept at the top of the home screen. The web app stores this as a separate array of IDs;
     * here it belongs to the group, which means one file, one write, and no way to end up
     * starring a group the list has forgotten.
     */
    val isStarred: Boolean = false,
    /** Kept, but out of the way. Starring and archiving say opposite things, so never both. */
    val isArchived: Boolean = false,
    @Serializable(with = ActiveParticipantSerializer::class)
    val activeParticipant: ActiveParticipant? = null,
    @Serializable(with = LenientDefaultSplitSerializer::class)
    val defaultSplit: DefaultSplit? = null,
    /** When this row last changed, in any way — opened, renamed, starred, answered for. */
    @Serializable(with = NullableInstantSerializer::class)
    val updatedAt: Instant? = null,
    /** When the group was last opened; "most recently first" sorts on this. */
    @Serializable(with = NullableInstantSerializer::class)
    val lastOpenedAt: Instant? = null,
    /**
     * The Spliit instance this group is on.
     *
     * A group ID means nothing on its own: the same list can hold a flatshare on spliit.app and
     * a family group on a server in somebody's hallway. Nil means "wherever this install points
     * by default", which is every list written before this existed.
     */
    val instanceURL: String? = null,
) {
    val id: String get() = groupId

    fun withTimestamps(now: Instant): RecentGroup = copy(updatedAt = now, lastOpenedAt = now)
}

/**
 * A stored split whose contents can fail: [SplitMode] has no unknown case, so a mode written by
 * a later version throws. Tolerated here rather than in the row decoder, because the whole point
 * of that decoder is that no group is ever lost — and a forgotten split is a tick box, while a
 * row that throws takes the entire list with it.
 */
object LenientDefaultSplitSerializer : KSerializer<DefaultSplit?> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: DefaultSplit?) {
        val jsonEncoder = encoder as JsonEncoder
        if (value == null) {
            jsonEncoder.encodeJsonElement(JsonNull)
        } else {
            jsonEncoder.encodeSerializableValue(DefaultSplit.serializer(), value)
        }
    }

    override fun deserialize(decoder: Decoder): DefaultSplit? {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        if (element is JsonNull) return null
        return runCatching {
            RecentGroupsCodec.json.decodeFromJsonElement(DefaultSplit.serializer(), element)
        }.getOrNull()
    }
}

/** The on-disk shape: a plain JSON array, the same one the iOS app keeps in its file. */
object RecentGroupsCodec {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        // Swift's synthesised encoding omits nil optionals, so the file does too.
        explicitNulls = false
    }

    fun encode(groups: List<RecentGroup>): String =
        json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(RecentGroup.serializer()),
            groups,
        )

    /**
     * Decodes the list, never throwing.
     *
     * A row that cannot be read at all is dropped rather than taking the file with it; a list
     * that fails to decode is a list of groups nobody can get back to.
     */
    fun decode(text: String?): List<RecentGroup> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(RecentGroup.serializer()),
                text,
            )
        }.getOrElse { recover(text) }
    }

    private fun recover(text: String): List<RecentGroup> {
        val array = runCatching { json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonArray }
            .getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(RecentGroup.serializer(), element) }.getOrNull()
        }
    }
}

/**
 * The rules that do not need storage, kept pure so they can be tested without Android.
 */
object RecentGroupsLogic {

    /** Most recently opened first; the group ID breaks a tie so two rows never swap places. */
    fun ordered(groups: List<RecentGroup>): List<RecentGroup> =
        groups.sortedWith(
            compareByDescending<RecentGroup> { it.lastOpenedAt ?: Instant.MIN }
                .thenBy { it.groupId },
        )

    /**
     * Adds a group, or moves it to the front and refreshes its name if it's already there.
     *
     * The stored flags, identity and split win over what [incoming] carries: otherwise renaming
     * a group would quietly unstar it, make the person holding the phone a stranger in it, and
     * forget how the group divides its expenses. The instance is the exception — the caller has
     * just been answered by a server, so it knows better than the list does where this group was
     * found.
     */
    fun remember(existing: RecentGroup?, incoming: RecentGroup, now: Instant): RecentGroup {
        val merged = if (existing == null) {
            incoming
        } else {
            incoming.copy(
                isStarred = existing.isStarred,
                isArchived = existing.isArchived,
                activeParticipant = existing.activeParticipant,
                defaultSplit = existing.defaultSplit,
                instanceURL = incoming.instanceURL ?: existing.instanceURL,
            )
        }
        return merged.withTimestamps(now)
    }

    /** Stars a group, or takes the star away. Starring un-archives; a group cannot be both. */
    fun setStarred(groups: List<RecentGroup>, groupId: String, isStarred: Boolean, now: Instant): List<RecentGroup> =
        modify(groups, groupId, now) {
            it.copy(
                isStarred = isStarred,
                isArchived = if (isStarred) false else it.isArchived,
            )
        }

    /** Archives a group, or brings it back. Archiving unstars, for the same reason. */
    fun setArchived(groups: List<RecentGroup>, groupId: String, isArchived: Boolean, now: Instant): List<RecentGroup> =
        modify(groups, groupId, now) {
            it.copy(
                isArchived = isArchived,
                isStarred = if (isArchived) false else it.isStarred,
            )
        }

    fun setActiveParticipant(
        groups: List<RecentGroup>,
        groupId: String,
        participant: ActiveParticipant,
        now: Instant,
    ): List<RecentGroup> = modify(groups, groupId, now) { it.copy(activeParticipant = participant) }

    /** Saving the same split again is not an edit, and must not restamp the row. */
    fun setDefaultSplit(
        groups: List<RecentGroup>,
        groupId: String,
        split: DefaultSplit,
        now: Instant,
    ): List<RecentGroup> {
        val current = groups.firstOrNull { it.groupId == groupId } ?: return groups
        if (current.defaultSplit == split) return groups
        return modify(groups, groupId, now) { it.copy(defaultSplit = split) }
    }

    fun forget(groups: List<RecentGroup>, groupId: String): List<RecentGroup> =
        groups.filterNot { it.groupId == groupId }

    /**
     * Writes an address onto every group that has none, so changing which instance is the default
     * can never silently move a group somewhere it isn't.
     *
     * Deliberately does not touch `updatedAt`: this is a device filling in what it already
     * believed, not an edit.
     */
    fun stampInstances(groups: List<RecentGroup>, url: String): List<RecentGroup> =
        groups.map { group -> if (group.instanceURL == null) group.copy(instanceURL = url) else group }

    /**
     * Who to credit for a change made from this phone, as the server's activity log wants it.
     *
     * Null is the honest answer twice over: for a phone that has never been asked, and for one
     * that answered "nobody". The log then says "Someone", which is exactly what is known.
     */
    fun actorId(groups: List<RecentGroup>, groupId: String, participants: List<Participant>): String? =
        ActiveParticipant.resolve(
            groups.firstOrNull { it.groupId == groupId }?.activeParticipant,
            participants,
        )?.participantId

    private fun modify(
        groups: List<RecentGroup>,
        groupId: String,
        now: Instant,
        change: (RecentGroup) -> RecentGroup,
    ): List<RecentGroup> = groups.map { group ->
        if (group.groupId == groupId) change(group).copy(updatedAt = now) else group
    }
}
