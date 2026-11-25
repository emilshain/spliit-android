package app.spliit.domain

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A group link somebody handed over on purpose: pasted into the app.
 *
 * It names two things, not one. A group ID means nothing without the server that issued it, and
 * taking the instance from the link is how somebody is let into a group on a server this device
 * has never talked to — which is most of what self-hosting is.
 *
 * The shapes that reach here, all naming the same group:
 * ```
 * https://spliit.app/groups/<id>
 * https://spliit.app/groups/<id>/expenses?ref=share   what the web app's QR code encodes
 * https://home.example.com/spliit/groups/<id>         self-hosted, in a subdirectory
 * <id>                                                what people paste out of an address bar
 * ```
 *
 * The instance is kept without a trailing slash, the same form [ServerInstance] stores, so a
 * group added from a link and one added any other way compare equal.
 */
data class GroupLink(
    val groupId: String,
    /** The instance the link names, or null for a bare ID — which names none. */
    val instanceUrl: String?,
) {
    companion object {

        /**
         * The link the web app's share dialog hands out, and the one this app shares in turn:
         * the group, opened on its expenses, tagged so a server can tell a shared link apart.
         *
         * The instance is trimmed rather than normalised here — it is the address the group is
         * already being read from, and a link that points somewhere else is a different group.
         */
        fun shareUrl(groupId: String, instanceUrl: String): String =
            "${instanceUrl.trimEnd('/')}/groups/$groupId/expenses?ref=share"

        /**
         * The group a URL names, or null for a URL that names none — and for anything that is
         * not an http(s) URL at all.
         *
         * This is the strict reading, and it is the one a scanned code gets. Anything can be
         * printed on a QR code, and a bare word off a Wi-Fi sticker is not somebody's group.
         */
        fun parseUrl(text: String): GroupLink? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null

            val url = trimmed.toHttpUrlOrNull() ?: return null
            val segments = url.pathSegments
            val index = segments.indexOf("groups")
            if (index < 0 || index + 1 >= segments.size) return null

            val id = segments[index + 1]
            if (id.isEmpty()) return null

            return GroupLink(id, instanceOf(url, index))
        }

        /**
         * The group a pasted value names: a URL, or the bare ID people tend to paste when they
         * copy out of the address bar of a group they already have open.
         */
        fun parsePasted(text: String): GroupLink? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null

            // A link copied out of an address bar often arrives without its scheme, and
            // "spliit.example.com/groups/x" parses as a path with no host at all. Only ever
            // assumed for something with a slash in it: "https://" in front of a bare ID would
            // make the ID the hostname.
            val candidate = if (trimmed.contains("://") || !trimmed.contains("/")) {
                trimmed
            } else {
                "https://$trimmed"
            }

            parseUrl(candidate)?.let { return it }

            // A bare ID: no slashes, no spaces.
            if (trimmed.contains("/") || trimmed.contains(" ")) return null
            return GroupLink(trimmed, null)
        }

        /**
         * Everything before `/groups/…`, keeping any subdirectory the instance is served from.
         * Dropping the prefix would point every request at the root of somebody's website.
         */
        private fun instanceOf(url: HttpUrl, groupsIndex: Int): String {
            val prefix = url.pathSegments.take(groupsIndex).filter { it.isNotEmpty() }
            val path = if (prefix.isEmpty()) "" else "/" + prefix.joinToString("/")
            val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
            // OkHttp canonicalises the host to lowercase, which is what stops a link typed with
            // a capital letter becoming a second instance beside the one it already is.
            return "${url.scheme}://${url.host}$port$path"
        }
    }
}
