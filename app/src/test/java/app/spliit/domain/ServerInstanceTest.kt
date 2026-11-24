package app.spliit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerInstanceTest {

    @Test
    fun `the default is spliit dot app over https`() {
        assertEquals("https://spliit.app", ServerInstance.DEFAULT_BASE_URL)
        assertTrue(ServerInstance.isDefault(ServerInstance.DEFAULT_BASE_URL))
    }

    @Test
    fun `a bare host gets https`() {
        assertEquals("https://spliit.app", ServerInstance.normalize("spliit.app"))
    }

    @Test
    fun `trailing slashes and whitespace are removed`() {
        assertEquals("https://spliit.app", ServerInstance.normalize("  https://spliit.app/  "))
        assertEquals("https://spliit.app", ServerInstance.normalize("https://spliit.app///"))
    }

    @Test
    fun `a path prefix is kept, because instances are hosted under one`() {
        assertEquals("https://example.com/spliit", ServerInstance.normalize("https://example.com/spliit/"))
    }

    @Test
    fun `a bare host keeps its port and defaults to https`() {
        // Defaulting to http for a bare host would send every expense over plaintext; an
        // instance that really is http has to say so.
        assertEquals("https://192.168.1.10:8080", ServerInstance.normalize("192.168.1.10:8080"))
    }

    @Test
    fun `an explicit http instance is allowed, for a LAN server`() {
        assertEquals("http://192.168.1.10:8080", ServerInstance.normalize("http://192.168.1.10:8080"))
    }

    @Test
    fun `nonsense is refused rather than stored`() {
        assertNull(ServerInstance.normalize(""))
        assertNull(ServerInstance.normalize("   "))
        assertNull(ServerInstance.normalize("not a url"))
        assertNull(ServerInstance.normalize("ftp://example.com"))
        // A group link is not an instance root.
        assertNull(ServerInstance.normalize("https://spliit.app/groups/abc?x=1"))
    }
}
