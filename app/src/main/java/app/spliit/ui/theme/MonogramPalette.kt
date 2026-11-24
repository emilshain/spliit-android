package app.spliit.ui.theme

/**
 * Picks the colour and the letters for a monogram.
 *
 * The colour has to be stable: the same person keeps the same colour between launches, and two
 * people looking at the same group on different phones see each other the same way. Kotlin's
 * own `hashCode` for a String is stable, but it is an implementation detail nobody promised —
 * hence the hash here, ported from the iOS app for exactly that reason.
 */
object MonogramPalette {

    /** How many colours the palette holds. */
    const val COUNT = 8

    /**
     * The colour for someone at a known position in a list — how participants are coloured.
     *
     * Position beats hashing here: four participants hashed into eight buckets collide more
     * often than not, and two people sharing a colour in the same group defeats the point.
     */
    fun indexAtPosition(position: Int): Int = ((position % COUNT) + COUNT) % COUNT

    /**
     * The colour for something with no position to speak of — how groups are coloured.
     *
     * The home screen reorders itself as groups are opened, so a group's colour has to come
     * from its identity rather than its row.
     *
     * @param seed the group's **ID**, not its name, so the colour survives a rename.
     */
    fun indexFor(seed: String): Int {
        // FNV-1a, 64-bit: a handful of lines, no dependencies, and well spread over the short
        // opaque IDs the API hands out.
        var hash: ULong = 0xcbf29ce484222325uL
        for (byte in seed.encodeToByteArray()) {
            hash = hash xor byte.toUByte().toULong()
            hash *= 0x100000001b3uL
        }
        return (hash % COUNT.toULong()).toInt()
    }

    /**
     * The one or two letters drawn inside the monogram.
     *
     * Empty for a nameless participant, which validation rules out — a blank chip is a quieter
     * failure than a placeholder glyph that looks like an error.
     */
    fun initials(name: String): String =
        name.split(' ', '\t', '\n', '\r')
            .filter { it.isNotEmpty() }
            .take(2)
            .mapNotNull { it.firstOrNull() }
            .joinToString("")
            .uppercase()
}
