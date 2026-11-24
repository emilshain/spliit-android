package app.spliit.domain

/**
 * Which Spliit instance a new group will be created on, while the form is open.
 *
 * A group's address belongs to the group rather than to the app, so this is a form field like
 * any other — and like the others it has to be able to hold something unfinished. Somebody
 * half-way through typing "spliit.exa" has not made a mistake yet, so the address is kept as
 * text and only becomes a URL once it parses as one.
 */
data class InstanceChoice(
    /** The instance chosen from the list, and what the group is created on unless typing. */
    val url: String,
    /** True once "Other server" is chosen. It stays true while the address is unusable, so the
     * field cannot disappear from under whoever is typing in it. */
    val isTypingAddress: Boolean = false,
    /** What has been typed so far, which is not an address until it is. */
    val address: String = "",
) {
    /** The instance to create on, or null while the typed address isn't one. */
    val resolved: String?
        get() = if (isTypingAddress) ServerInstance.normalize(address) else url

    val isValid: Boolean get() = resolved != null

    /** Takes an instance chosen from the list. Also the way back from a typed address. */
    fun use(url: String): InstanceChoice = copy(url = url, isTypingAddress = false, address = "")

    /** Switches to typing an address that isn't in the list yet. */
    fun typeAddress(): InstanceChoice = copy(isTypingAddress = true)
}
