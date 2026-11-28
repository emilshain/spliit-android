package app.spliit.ui.group

// The log of what happened to the group. Part of [GroupScreenModel].

import app.spliit.data.remote.SpliitApi
import app.spliit.data.remote.TrpcClient
import app.spliit.domain.Failures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Fetches the log the first time its screen is looked at.
 *
 * Deliberately not part of [start]: the log is one screen nothing else on the group needs, so
 * loading it with the rest would put a request every group open pays for behind the three that
 * draw the screen.
 */
suspend fun GroupScreenModel.loadActivitiesIfNeeded() {
    if (activities.isNotEmpty() || didLoadActivities) return
    loadFirstActivityPage()
}

suspend fun GroupScreenModel.retryActivities() {
    // The group may be what failed, and the log cannot name anybody without it.
    val current = client ?: return
    if (group == null) loadGroup(current)
    loadFirstActivityPage()
}

suspend fun GroupScreenModel.refreshActivities() {
    loadFirstActivityPage()
}

suspend fun GroupScreenModel.loadFirstActivityPage() {
    val current = client ?: return
    try {
        val response = current.call(
            SpliitApi.activities(groupId, cursor = 0, limit = GroupScreenModel.PAGE_SIZE),
        )
        activities = response.activities
        hasMoreActivities = response.hasMore
        nextActivityCursor = response.nextCursor
        activitiesFailure = null
        didLoadActivities = true
        // The snapshot is what the log is read from with no network, so it has to hold it.
        persistSnapshot()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // The log was in the snapshot; without a network it is still the log.
        val cached = container.groupCache.load(groupId)?.activities.orEmpty()
        if (cached.isNotEmpty() && isOfflineNetworkFailure(error)) {
            activities = cached
            hasMoreActivities = false
            activitiesFailure = null
            didLoadActivities = true
            isOffline = true
            return
        }
        activitiesFailure = Failures.message(error)
        didLoadActivities = true
    }
}

fun GroupScreenModel.loadNextActivityPage() {
    if (!hasMoreActivities || isLoadingMoreActivities) return
    scope.launch {
        val current = client ?: return@launch
        isLoadingMoreActivities = true
        try {
            val response = current.call(
                SpliitApi.activities(groupId, cursor = nextActivityCursor, limit = GroupScreenModel.PAGE_SIZE),
            )
            // The log grows at the top, so a page fetched after something new was recorded
            // repeats a row rather than skipping one. Same guard as the expense list.
            val known = activities.mapTo(HashSet()) { it.id }
            activities = activities + response.activities.filterNot { it.id in known }
            hasMoreActivities = response.hasMore
            nextActivityCursor = response.nextCursor
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            hasMoreActivities = false
        } finally {
            isLoadingMoreActivities = false
        }
    }
}

/** Re-reads the log after a change that wrote to it — but only if it has been read once. */
suspend fun GroupScreenModel.reloadActivitiesIfLoaded(client: TrpcClient) {
    if (!didLoadActivities) return
    loadFirstActivityPage()
}

// MARK: - Search
