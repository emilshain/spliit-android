package app.spliit.ui.group

// Looking through the group's expenses. Part of [GroupScreenModel].

import app.spliit.data.remote.SpliitApi
import app.spliit.data.remote.TrpcClient
import app.spliit.domain.Failures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Answers [text], after a pause long enough to mean the typing has stopped.
 *
 * Driven by a `LaunchedEffect` keyed on the field, which cancels the previous call on every
 * keystroke — so the delay below is only ever reached by the last one. Cancellation lands in
 * the delay, before any request goes out and before [filter] moves, which keeps a half-typed
 * word from ever being the state the results are showing.
 */
suspend fun GroupScreenModel.search(text: String) {
    val trimmed = text.trim()
    val requested = trimmed.ifEmpty { null }
    if (requested == filter) return

    try {
        delay(searchDebounceMillis)
    } catch (error: CancellationException) {
        throw error
    }

    filter = requested
    if (requested == null) {
        // An emptied field is not a search for nothing: drop the results and go back to the
        // prompt rather than asking the server for the whole group again.
        searchResults = emptyList()
        hasMoreSearchResults = false
        searchFailure = null
        didLoadSearch = false
        return
    }
    if (isOffline) {
        loadLocalSearch(requested)
        return
    }
    loadFirstSearchPage(requested)
}

suspend fun GroupScreenModel.loadFirstSearchPage(query: String) {
    val current = client ?: return
    isSearching = true
    searchFailure = null
    try {
        val response = current.call(
            SpliitApi.expenses(groupId, cursor = 0, limit = GroupScreenModel.PAGE_SIZE, filter = query),
        )
        // The field may have moved on while this was in flight; a stale page must not become
        // the answer to a question nobody asked.
        if (filter != query) return
        searchResults = withoutPendingDeletion(response.expenses)
        hasMoreSearchResults = response.hasMore
        nextSearchCursor = response.nextCursor
        didLoadSearch = true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        if (filter == query) {
            if (group != null && isOfflineNetworkFailure(error)) {
                isOffline = true
                loadLocalSearch(query)
            } else {
                searchFailure = Failures.message(error)
            }
        }
    } finally {
        if (filter == query) isSearching = false
    }
}

/**
 * The same search, answered from the cached expenses.
 *
 * The server matches titles and notes; the list rows carry the title only, so that is what
 * this matches. It is the honest offline answer, not a claim of a full-text search.
 */
fun GroupScreenModel.loadLocalSearch(query: String) {
    val needle = query.trim()
    searchResults = displayExpenses.filter { it.title.contains(needle, ignoreCase = true) }
    hasMoreSearchResults = false
    nextSearchCursor = 0
    searchFailure = null
    didLoadSearch = true
    isSearching = false
}

fun GroupScreenModel.loadNextSearchPage() {
    val query = filter ?: return
    if (!hasMoreSearchResults || isLoadingMoreSearchResults) return
    scope.launch {
        val current = client ?: return@launch
        isLoadingMoreSearchResults = true
        try {
            val response = current.call(
                SpliitApi.expenses(
                    groupId,
                    cursor = nextSearchCursor,
                    limit = GroupScreenModel.PAGE_SIZE,
                    filter = query,
                ),
            )
            if (filter != query) return@launch
            val known = searchResults.mapTo(HashSet()) { it.id }
            searchResults = searchResults + withoutPendingDeletion(response.expenses)
                .filterNot { it.id in known }
            hasMoreSearchResults = response.hasMore
            nextSearchCursor = response.nextCursor
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            hasMoreSearchResults = false
        } finally {
            isLoadingMoreSearchResults = false
        }
    }
}

/** Re-runs the current search, for when an expense was edited or deleted underneath it. */
suspend fun GroupScreenModel.reloadSearchIfRequested(client: TrpcClient) {
    val query = filter ?: return
    loadFirstSearchPage(query)
}

// MARK: - Deleting, with a way back
