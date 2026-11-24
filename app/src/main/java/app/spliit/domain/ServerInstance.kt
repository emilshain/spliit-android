package app.spliit.domain

import java.net.URI

/**
 * Which Spliit server the app talks to.
 *
 * A group ID means nothing on its own: the same list can hold a flatshare on spliit.app and a
 * family group on a server in somebody's hallway, and asking the wrong one about either gets
 * back a group that does not exist. So the address belongs to the group, and this is where it
 * is parsed and where the default lives.
 */
object ServerInstance {

    /** The instance every group falls back to, matching the iOS app. */
    const val DEFAULT_BASE_URL: String = "https://spliit.app"

    /**
     * The instance root for [raw], or null if it is not an address.
     *
     * Accepts what people actually type: a bare host (`spliit.app`), a host with a path
     * (`example.com/spliit`), a trailing slash, or surrounding whitespace. A scheme is added
     * when missing — https, because a shared group link is https and defaulting to http would
     * silently send every expense over plaintext.
     */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // Any scheme at all is honoured here and rejected below, so "ftp://host" fails as an
        // unsupported scheme rather than being read as a host called "ftp".
        val withScheme = if (SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"

        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null

        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        // A URL with credentials, a query or a fragment is not an instance root.
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null

        val port = if (uri.port == -1) "" else ":${uri.port}"
        val path = uri.path.orEmpty().trimEnd('/')
        return "$scheme://$host$port$path"
    }

    /** Whether this address is the default instance. */
    fun isDefault(baseUrl: String): Boolean =
        baseUrl.trimEnd('/').equals(DEFAULT_BASE_URL, ignoreCase = true)

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
}
