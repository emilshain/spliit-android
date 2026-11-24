package app.spliit.data.local

import app.spliit.data.model.Participant
import app.spliit.data.model.SplitMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The store itself, not just the codec: every assertion goes through [RecentGroupsRepository],
 * so a broken `edit`, a lost write or a section that drops a group fails here.
 */
class RecentGroupsRepositoryTest {

    private val store = InMemoryPreferencesDataStore()
    private var clock: Instant = Instant.parse("2026-08-01T10:00:00Z")

    private fun repository() = RecentGroupsRepository(store) { clock }

    private fun advanceTo(instant: Instant) {
        clock = instant
    }

    @Test
    fun `a fresh install has no groups and an empty home screen`() = runTest {
        val repository = repository()

        assertTrue(repository.snapshot().isEmpty())
        assertTrue(repository.sections.first().isEmpty)
    }

    @Test
    fun `remembering a group writes it where the flow can see it`() = runTest {
        val repository = repository()

        repository.remember(RecentGroup(groupId = "g1", groupName = "Flat"))

        val stored = repository.groups.first().single()
        assertEquals("g1", stored.groupId)
        assertEquals("Flat", stored.groupName)
        assertEquals(clock, stored.updatedAt)
        assertEquals(clock, stored.lastOpenedAt)
    }

    @Test
    fun `a second remember renames in place rather than adding a row`() = runTest {
        val repository = repository()
        repository.remember(RecentGroup("g1", "Flat"))
        repository.setStarred(isStarred = true, groupId = "g1")
        advanceTo(Instant.parse("2026-08-02T10:00:00Z"))

        repository.remember(RecentGroup("g1", "Flat 3B"))

        val groups = repository.snapshot()
        assertEquals(1, groups.size)
        assertEquals("Flat 3B", groups.single().groupName)
        assertTrue("a rename must not unstar the group", groups.single().isStarred)
        assertEquals(advanceToTime, groups.single().updatedAt)
    }

    @Test
    fun `the home screen sections partition the list`() = runTest {
        val repository = repository()
        repository.remember(RecentGroup("starred", "Starred"))
        repository.remember(RecentGroup("recent", "Recent"))
        repository.remember(RecentGroup("archived", "Archived"))
        repository.setStarred(isStarred = true, groupId = "starred")
        repository.setArchived(isArchived = true, groupId = "archived")

        val sections = repository.sections.first()

        assertEquals(listOf("starred"), sections.starred.map { it.groupId })
        assertEquals(listOf("recent"), sections.recent.map { it.groupId })
        assertEquals(listOf("archived"), sections.archived.map { it.groupId })
        assertFalse(sections.isEmpty)
    }

    @Test
    fun `starring an archived group brings it back out of the archive`() = runTest {
        val repository = repository()
        repository.remember(RecentGroup("g1", "Flat", isArchived = true))

        repository.setStarred(isStarred = true, groupId = "g1")

        val group = repository.snapshot().single()
        assertTrue(group.isStarred)
        assertFalse(group.isArchived)
    }

    @Test
    fun `the identity round trips through the store`() = runTest {
        val repository = repository()
        repository.remember(RecentGroup("g1", "Flat"))

        assertNull(repository.activeParticipant("g1"))

        repository.setActiveParticipant(ActiveParticipant.Participant("ana"), "g1")
        assertEquals(ActiveParticipant.Participant("ana"), repository.activeParticipant("g1"))

        repository.setActiveParticipant(ActiveParticipant.Nobody, "g1")
        assertEquals(
            "an answer of nobody must not read back as unanswered",
            ActiveParticipant.Nobody,
            repository.activeParticipant("g1"),
        )
    }

    @Test
    fun `a remembered split is read back, and saving it twice does not restamp`() = runTest {
        val repository = repository()
        repository.remember(RecentGroup("g1", "Flat"))
        val split = DefaultSplit(SplitMode.BY_PERCENTAGE, mapOf("ana" to 7000, "bruno" to 3000))

        repository.setDefaultSplit(split, "g1")
        assertEquals(split, repository.defaultSplit("g1"))
        val stamped = repository.snapshot().single().updatedAt

        advanceTo(Instant.parse("2026-09-01T10:00:00Z"))
        repository.setDefaultSplit(split, "g1")

        assertEquals("an unchanged split is not an edit", stamped, repository.snapshot().single().updatedAt)
    }

    @Test
    fun `the instance is remembered per group and blanks are filled in`() = runTest {
        val repository = repository()
        repository.remember(RecentGroup("g1", "Flat"))
        repository.remember(RecentGroup("g2", "Trip", instanceURL = "https://other.example"))

        assertNull(repository.instanceUrl("g1"))

        repository.stampInstances("https://spliit.app")

        assertEquals("https://spliit.app", repository.instanceUrl("g1"))
        assertEquals(
            "an existing address is not overwritten",
            "https://other.example",
            repository.instanceUrl("g2"),
        )
    }

    @Test
    fun `forgetting removes exactly one group`() = runTest {
        val repository = repository()
        repository.remember(RecentGroup("g1", "Flat"))
        repository.remember(RecentGroup("g2", "Trip"))

        repository.forget("g1")

        assertEquals(listOf("g2"), repository.snapshot().map { it.groupId })
    }

    @Test
    fun `the actor is named only for an identity the group still has`() = runTest {
        val repository = repository()
        val ana = Participant("ana", "Ana")
        repository.remember(RecentGroup("g1", "Flat"))

        assertNull(repository.actorId("g1", listOf(ana)))

        repository.setActiveParticipant(ActiveParticipant.Participant("ana"), "g1")
        assertEquals("ana", repository.actorId("g1", listOf(ana)))
        assertNull("a participant who left names nobody", repository.actorId("g1", emptyList()))
    }

    @Test
    fun `the list is ordered most recently opened first`() = runTest {
        val repository = repository()
        repository.remember(RecentGroup("first", "First"))
        advanceTo(Instant.parse("2026-08-02T10:00:00Z"))
        repository.remember(RecentGroup("second", "Second"))
        advanceTo(Instant.parse("2026-08-03T10:00:00Z"))
        // Re-opening the older group moves it back to the top.
        repository.remember(RecentGroup("first", "First"))

        assertEquals(listOf("first", "second"), repository.snapshot().map { it.groupId })
    }

    @Test
    fun `a corrupt stored value reads as no groups instead of throwing`() = runTest {
        store.putRaw(key = GROUPS, value = "{not a list")
        val repository = repository()

        assertTrue(repository.snapshot().isEmpty())
        // And the store still works afterwards.
        repository.remember(RecentGroup("g1", "Flat"))
        assertEquals(1, repository.snapshot().size)
    }

    @Test
    fun `two repositories over one store see the same list`() = runTest {
        val first = repository()
        first.remember(RecentGroup("g1", "Flat"))

        val second = repository()

        assertEquals(listOf("g1"), second.snapshot().map { it.groupId })
    }

    private companion object {
        val GROUPS = androidx.datastore.preferences.core.stringPreferencesKey("groups")
        val advanceToTime: Instant = Instant.parse("2026-08-02T10:00:00Z")
    }
}
