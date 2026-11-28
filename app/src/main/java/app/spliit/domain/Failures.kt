package app.spliit.domain

import app.spliit.data.remote.TrpcClientException
import app.spliit.data.remote.TrpcServerException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * What went wrong, in a sentence that names the cause.
 *
 * A failed request reaches the screen as whatever Kotlin threw, and that is written for a
 * developer: "Unable to resolve host "spliit.app": No address associated with hostname" is the
 * phone saying it has no network, but read on a screen it says the app is broken. Every failure
 * the person sees goes through here instead, so being offline is named as being offline and a
 * server that is down is named as a server that is down.
 *
 * The cause chain is inspected before any message is used, because OkHttp's own failures arrive
 * wrapped in a client exception carrying the platform's wording.
 */
object Failures {

    const val Generic = "Something went wrong. Try again."

    /** No network, or no DNS: the one the user can actually do something about. */
    const val Offline = "You’re offline. Check your connection and try again."

    const val Timeout = "The server took too long to answer. Try again."

    /** A network, but nothing listening: the server is down rather than the phone. */
    const val Unreachable = "Couldn’t reach this Spliit server. It may be down."

    const val Insecure = "Couldn’t open a secure connection to this server."

    const val ServerError = "This Spliit server ran into a problem. Try again in a moment."

    const val Missing = "That isn’t on this server."

    const val Refused = "This server didn’t accept the change."

    const val Unreadable = "The server answered in a way this app couldn’t read."

    /**
     * The sentence for [error], or [fallback] for a failure with nothing better to say — a local
     * problem whose message is already written for a person, such as a file that could not be
     * opened.
     */
    fun message(error: Throwable, fallback: String = Generic): String {
        val chain = generateSequence(error) { it.cause }.take(8).toList()

        // Most specific first: a timeout is also an IOException, and a refusal is also one.
        if (chain.any { it is UnknownHostException }) return Offline
        if (chain.any { it is SocketTimeoutException }) return Timeout
        if (chain.any { it is SSLException }) return Insecure
        if (chain.any { it is ConnectException }) return Unreachable

        // The server's answer can also arrive wrapped in another failure; it still describes
        // what happened better than the wrapper does.
        chain.filterIsInstance<TrpcServerException>().firstOrNull()?.let {
            return serverMessage(it, fallback)
        }
        chain.filterIsInstance<TrpcClientException>().firstOrNull()?.let {
            return clientMessage(it, fallback)
        }

        return when (error) {
            // A socket closed mid-answer, or no route to the host: still the network's fault,
            // and still worth saying plainly.
            is IOException -> Unreachable

            else -> fallback
        }
    }

    private fun clientMessage(error: TrpcClientException, fallback: String): String =
        if (error.readable) error.message?.takeIf { it.isNotBlank() } ?: fallback
        else Unreadable

    /**
     * A refusal from the server. Validation messages are kept because they are written for the
     * person filling the form ("Amount must be positive"); everything else is replaced with a
     * sentence that says what happened without naming a status code.
     */
    private fun serverMessage(error: TrpcServerException, fallback: String): String = when {
        error.httpStatus == 404 -> Missing
        error.httpStatus == 401 || error.httpStatus == 403 -> Refused
        error.httpStatus == 408 -> Timeout
        error.httpStatus >= 500 -> ServerError
        error.code == "BAD_REQUEST" || error.httpStatus == 400 ->
            error.message?.takeIf { it.isNotBlank() } ?: fallback
        else -> fallback
    }
}
