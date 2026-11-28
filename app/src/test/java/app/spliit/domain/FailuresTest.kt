package app.spliit.domain

import app.spliit.data.remote.TrpcClientException
import app.spliit.data.remote.TrpcServerException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The screens show what this says, so these are the sentences a person reads when something goes
 * wrong — including the one that used to read "Unable to resolve host "spliit.app": No address
 * associated with hostname" for a phone that simply had no signal.
 */
class FailuresTest {

    @Test
    fun `no address for a hostname reads as being offline`() {
        val wrapped = TrpcClientException(
            "Unable to resolve host \"spliit.app\": No address associated with hostname",
            UnknownHostException("spliit.app"),
            readable = false,
        )
        assertEquals(Failures.Offline, Failures.message(wrapped))
    }

    @Test
    fun `a refusal with no network reads as offline`() {
        assertEquals(Failures.Offline, Failures.message(UnknownHostException("spliit.app")))
    }

    @Test
    fun `a server that is not listening is not blamed on the connection`() {
        assertEquals(Failures.Unreachable, Failures.message(ConnectException("ECONNREFUSED")))
    }

    @Test
    fun `a slow answer is a timeout, not a broken app`() {
        assertEquals(Failures.Timeout, Failures.message(SocketTimeoutException("timeout")))
    }

    @Test
    fun `a status the app cannot read is not shown as it arrived`() {
        val unreadable = TrpcClientException("Unexpected response (500): <html>…", readable = false)
        assertEquals(Failures.Unreadable, Failures.message(unreadable))
    }

    @Test
    fun `a message written for a person is kept`() {
        val curated = TrpcClientException("This Spliit server doesn’t accept receipt photos.")
        assertEquals("This Spliit server doesn’t accept receipt photos.", Failures.message(curated))
    }

    @Test
    fun `a failing server is named as one`() {
        val error = TrpcServerException(
            code = "INTERNAL_SERVER_ERROR",
            message = "PrismaClientKnownRequestError",
            httpStatus = 500,
            path = "groups.get",
        )
        assertEquals(Failures.ServerError, Failures.message(error))
        assertEquals(Failures.ServerError, Failures.message(IOException("unexpected end of stream", error)))
    }

    @Test
    fun `a rejected form keeps the server's own explanation`() {
        val error = TrpcServerException(
            code = "BAD_REQUEST",
            message = "Amount must be positive",
            httpStatus = 400,
            path = "groups.expenses.create",
        )
        assertEquals("Amount must be positive", Failures.message(error))
    }

    @Test
    fun `a missing group says it is missing`() {
        val error = TrpcServerException(
            code = "NOT_FOUND",
            message = "No group with that ID",
            httpStatus = 404,
            path = "groups.get",
        )
        assertEquals(Failures.Missing, Failures.message(error))
    }

    @Test
    fun `a local failure keeps its own sentence`() {
        val refused = IllegalArgumentException("That file couldn’t be opened.")
        assertEquals(
            "Couldn’t write the file.",
            Failures.message(refused, "Couldn’t write the file."),
        )
    }
}
