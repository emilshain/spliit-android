package app.spliit.data.local

import app.spliit.data.model.Participant
import app.spliit.data.model.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecentGroupsLogicTest {

    private val t1 = Instant.parse("2026-08-01T10:00:00Z")
    private val t2 = Instant.parse("2026-08-02T10:00:00Z")
    private val t3 = Instant.parse("2026-08-03T10:00:00Z")

    private fun group(
        id: String,
        name: String = id,
        opened: Instant? = null,
        starred: Boolean = false,
        archived: Boolean = false,
        identity: ActiveParticipant? = null,
        split: DefaultSplit? = null,
        instance: String? = null,
    ) = RecentGroup(
        groupId = id,
        groupName = name,
        isStarred = starred,
        isArchived = archived,
        activeParticipant = identity,
        defaultSplit = split,
        lastOpenedAt = opened,
        updatedAt = opened,
        instanceURL = instance,
    )

    @Test
    fun `most recently opened first, with the id breaking a tie`() {
        val ordered = RecentGroupsLogic.ordered(
            listOf(
                group("b", opened = t1),
                group("c", opened = t3),
                group("a", opened = t1),
                group("d"),
            ),
        )

        // The unopened one has no time at all, so it sorts last rather than jumping to the top.
        assertEquals(listOf("c", "a", "b", "d"), ordered.map { it.groupId })
    }

    /**
     * Renaming a group must not cost the star, the identity, or the remembered split — that is
     * the whole reason `remember` merges rather than replaces.
     */
    @Test
    fun `remembering an existing group keeps the flags the row was written with`() {
        val existing = group(
            "g1",
            name = "Flat",
            opened = t1,
            starred = true,
            identity = ActiveParticipant.Participant("ana"),
            split = DefaultSplit(SplitMode.BY_SHARES, mapOf("ana" to 200, "bruno" to 100)),
            instance = "https://spliit.app",
        )

        val remembered = RecentGroupsLogic.remember(existing, group("g1", name = "Flat 3B"), t2)

        assertEquals("Flat 3B", remembered.groupName)
        assertTrue(remembered.isStarred)
        assertEquals(ActiveParticipant.Participant("ana"), remembered.activeParticipant)
        assertEquals(SplitMode.BY_SHARES, remembered.defaultSplit?.splitMode)
        assertEquals("https://spliit.app", remembered.instanceURL)
        assertEquals(t2, remembered.updatedAt)
        assertEquals(t2, remembered.lastOpenedAt)
    }

    @Test
    fun `the server that answered knows where the group lives`() {
        val existing = group("g1", instance = "https://old.example")
        val incoming = group("g1", instance = "https://new.example")

        assertEquals("https://new.example", RecentGroupsLogic.remember(existing, incoming, t2).instanceURL)
        // A caller that does not know an address must not erase the one already stored.
        assertEquals(
            "https://old.example",
            RecentGroupsLogic.remember(existing, group("g1"), t2).instanceURL,
        )
    }

    @Test
    fun `a group remembering itself twice is not a second row`() {
        val remembered = RecentGroupsLogic.remember(null, group("g1"), t2)
        assertEquals("g1", remembered.groupId)
        assertFalse(remembered.isStarred)
        assertNull(remembered.activeParticipant)
    }

    @Test
    fun `starring un-archives, archiving unstars`() {
        val archived = listOf(group("g1", archived = true))
        val starred = RecentGroupsLogic.setStarred(archived, "g1", true, t2).single()
        assertTrue(starred.isStarred)
        assertFalse("a group cannot be both", starred.isArchived)

        val starredRow = listOf(group("g1", starred = true))
        val broughtBack = RecentGroupsLogic.setArchived(starredRow, "g1", true, t2).single()
        assertTrue(broughtBack.isArchived)
        assertFalse(broughtBack.isStarred)
    }

    @Test
    fun `removing a star leaves an archive alone`() {
        val group = RecentGroupsLogic.setStarred(listOf(group("g1", starred = true)), "g1", false, t2)
        assertFalse(group.single().isStarred)
        assertFalse(group.single().isArchived)
    }

    @Test
    fun `saving the same split again is not an edit`() {
        val split = DefaultSplit(SplitMode.BY_SHARES, mapOf("ana" to 100))
        val before = listOf(group("g1", opened = t1, split = split))

        val after = RecentGroupsLogic.setDefaultSplit(before, "g1", split, t3)

        assertEquals(before, after)
        assertEquals("an unchanged row must not be restamped", t1, after.single().updatedAt)
    }

    @Test
    fun `a changed split is stamped`() {
        val before = listOf(group("g1", opened = t1))
        val after = RecentGroupsLogic.setDefaultSplit(
            before,
            "g1",
            DefaultSplit(SplitMode.BY_PERCENTAGE, mapOf("ana" to 6000, "bruno" to 4000)),
            t3,
        )

        assertEquals(t3, after.single().updatedAt)
        assertEquals(SplitMode.BY_PERCENTAGE, after.single().defaultSplit?.splitMode)
    }

    @Test
    fun `forgetting removes only that group`() {
        val remaining = RecentGroupsLogic.forget(listOf(group("g1"), group("g2")), "g1")
        assertEquals(listOf("g2"), remaining.map { it.groupId })
    }

    @Test
    fun `stamping instances fills the blanks and touches no timestamps`() {
        val groups = listOf(group("g1", opened = t1), group("g2", opened = t2, instance = "https://other.example"))

        val stamped = RecentGroupsLogic.stampInstances(groups, "https://spliit.app")

        assertEquals("https://spliit.app", stamped[0].instanceURL)
        assertEquals(t1, stamped[0].updatedAt)
        assertEquals("https://other.example", stamped[1].instanceURL)
    }

    @Test
    fun `the actor is only named when this phone has said who it is`() {
        val ana = Participant("ana", "Ana")
        val participants = listOf(ana)

        assertEquals(
            "ana",
            RecentGroupsLogic.actorId(
                listOf(group("g1", identity = ActiveParticipant.Participant("ana"))),
                "g1",
                participants,
            ),
        )
        assertNull(
            RecentGroupsLogic.actorId(
                listOf(group("g1", identity = ActiveParticipant.Nobody)),
                "g1",
                participants,
            ),
        )
        assertNull(
            "a participant who has left names nobody",
            RecentGroupsLogic.actorId(
                listOf(group("g1", identity = ActiveParticipant.Participant("chloe"))),
                "g1",
                participants,
            ),
        )
    }

    @Test
    fun `a split naming somebody who left does not apply`() {
        val ana = Participant("ana", "Ana")
        val bruno = Participant("bruno", "Bruno")

        assertTrue(DefaultSplit(SplitMode.BY_SHARES, mapOf("ana" to 7000, "bruno" to 3000)).appliesTo(listOf(ana, bruno)))
        assertFalse(DefaultSplit(SplitMode.BY_SHARES, mapOf("ana" to 7000, "chloe" to 3000)).appliesTo(listOf(ana, bruno)))
        assertTrue("a newcomer was never in it", DefaultSplit(SplitMode.BY_SHARES, mapOf("ana" to 100)).appliesTo(listOf(ana, bruno)))
        assertTrue("mode-only splits always apply", DefaultSplit(SplitMode.BY_AMOUNT).appliesTo(listOf(ana)))
    }
}
