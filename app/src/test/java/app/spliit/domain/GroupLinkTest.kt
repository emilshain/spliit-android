package app.spliit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The link somebody hands over on purpose. The parsing is the looser reading given to something
 * typed out — the instance is kept in the same no-trailing-slash form [ServerInstance] stores.
 */
class GroupLinkTest {

    @Test
    fun `a group URL names its group`() {
        assertEquals("abc123", GroupLink.parseUrl("https://spliit.app/groups/abc123")?.groupId)
    }

    /** What the web app's share dialog encodes: a link into the expenses tab, with a query. */
    @Test
    fun `the QR code the web app draws names its group`() {
        assertEquals(
            "abc123",
            GroupLink.parseUrl("https://spliit.app/groups/abc123/expenses?ref=share")?.groupId,
        )
    }

    @Test
    fun `a link to any instance names its group`() {
        assertEquals("xyz", GroupLink.parseUrl("http://localhost:3009/groups/xyz")?.groupId)
        assertEquals("xyz", GroupLink.parseUrl("https://spliit.example.com/groups/xyz")?.groupId)
    }

    @Test
    fun `a URL that names no group is not a group link`() {
        assertNull(GroupLink.parseUrl("https://spliit.app")?.groupId)
        assertNull(GroupLink.parseUrl("https://spliit.app/groups")?.groupId)
        assertNull(GroupLink.parseUrl("https://spliit.app/groups/")?.groupId)
        assertNull(GroupLink.parseUrl("https://example.com/")?.groupId)
    }

    /** A QR code can carry anything at all, which is why the scanned reading is the strict one. */
    @Test
    fun `text that is not a URL is not a scanned group link`() {
        assertNull(GroupLink.parseUrl("abc123")?.groupId)
        assertNull(GroupLink.parseUrl("WIFI:S:Cafe;T:WPA;P:hunter2;;")?.groupId)
        assertNull(GroupLink.parseUrl("")?.groupId)
    }

    /** Pasting is the looser reading: people copy the ID out of the address bar as often. */
    @Test
    fun `a bare ID can be pasted`() {
        assertEquals("abc123", GroupLink.parsePasted("abc123")?.groupId)
        assertEquals("abc123", GroupLink.parsePasted("  abc123\n")?.groupId)
    }

    @Test
    fun `a pasted link is read the same way a scanned one is`() {
        assertEquals(
            "abc123",
            GroupLink.parsePasted("https://spliit.app/groups/abc123/expenses")?.groupId,
        )
    }

    @Test
    fun `a pasted value with no ID in it names nothing`() {
        assertNull(GroupLink.parsePasted("")?.groupId)
        assertNull(GroupLink.parsePasted("   ")?.groupId)
        assertNull(GroupLink.parsePasted("two words")?.groupId)
        assertNull(GroupLink.parsePasted("not/a/link")?.groupId)
    }

    // MARK: - The server the link names

    @Test
    fun `a link names the instance its group is on`() {
        assertEquals(
            "https://spliit.app",
            GroupLink.parseUrl("https://spliit.app/groups/abc123")?.instanceUrl,
        )
        assertEquals(
            "https://spliit.example.com",
            GroupLink.parseUrl("https://spliit.example.com/groups/abc123/expenses?ref=share")?.instanceUrl,
        )
    }

    /** The port is part of the address, so two instances on one host stay two instances. */
    @Test
    fun `a plain http address keeps its scheme and its port`() {
        assertEquals(
            "http://localhost:3009",
            GroupLink.parseUrl("http://localhost:3009/groups/abc123")?.instanceUrl,
        )
    }

    /** An instance served from a subdirectory keeps it. */
    @Test
    fun `an instance in a subdirectory keeps its path`() {
        assertEquals(
            "https://home.example.com/spliit",
            GroupLink.parseUrl("https://home.example.com/spliit/groups/abc123")?.instanceUrl,
        )
    }

    @Test
    fun `the address is read case-insensitively`() {
        assertEquals(
            "https://spliit.app",
            GroupLink.parseUrl("https://Spliit.App/groups/abc123")?.instanceUrl,
        )
    }

    /** A link copied out of an address bar often loses its scheme on the way. */
    @Test
    fun `a pasted link without its scheme still names its instance`() {
        val link = GroupLink.parsePasted("spliit.example.com/groups/abc123")

        assertEquals("abc123", link?.groupId)
        assertEquals("https://spliit.example.com", link?.instanceUrl)
    }

    /** A bare ID names no server at all, which is different from naming the wrong one. */
    @Test
    fun `a bare ID names no instance`() {
        assertNull(GroupLink.parsePasted("abc123")?.instanceUrl)
    }
}
