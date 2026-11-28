package app.spliit.data.remote

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the client every instance is called through will encrypt with, and what it refuses.
 *
 * A group holds participants' names and what they owe, so an instance that can only negotiate
 * TLS 1.0 is not one the app should still be handing that to.
 */
class TransportSecurityTest {

    private val client = TrpcClient.defaultCallFactory as OkHttpClient

    @Test
    fun `an encrypted call stops at TLS 1_2, never below`() {
        val tls = client.connectionSpecs.single { it.isTls }
        assertEquals(
            setOf(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2),
            tls.tlsVersions.orEmpty().toSet(),
        )
    }

    @Test
    fun `a self-hosted http instance is still reachable`() {
        assertTrue(client.connectionSpecs.any { it === ConnectionSpec.CLEARTEXT })
    }
}
