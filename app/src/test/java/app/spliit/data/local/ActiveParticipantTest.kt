package app.spliit.data.local

import app.spliit.data.model.Participant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The identity is three states, not two: unanswered (`null`), a participant, and an explicit
 * "nobody" for a shared phone. `nil` is the question; [ActiveParticipant.Nobody] is an answer.
 */
class ActiveParticipantTest {

    private val ana = Participant("ana", "Ana")
    private val bruno = Participant("bruno", "Bruno")

    @Test
    fun `a participant is stored as the bare id`() {
        val json = RecentGroupsCodec.encode(
            listOf(RecentGroup("g1", "Flat", activeParticipant = ActiveParticipant.Participant("ana"))),
        )

        assertTrue("expected a bare id, got $json", json.contains("\"activeParticipant\":\"ana\""))
    }

    @Test
    fun `nobody is stored as an empty string, not as an absent key`() {
        val json = RecentGroupsCodec.encode(
            listOf(RecentGroup("g1", "Flat", activeParticipant = ActiveParticipant.Nobody)),
        )

        assertTrue("expected an empty string, got $json", json.contains("\"activeParticipant\":\"\""))
        val decoded = RecentGroupsCodec.decode(json).single()
        assertEquals(ActiveParticipant.Nobody, decoded.activeParticipant)
    }

    @Test
    fun `unanswered is an absent key`() {
        val json = RecentGroupsCodec.encode(listOf(RecentGroup("g1", "Flat")))

        assertTrue("expected no key, got $json", !json.contains("activeParticipant"))
        assertNull(RecentGroupsCodec.decode(json).single().activeParticipant)
    }

    @Test
    fun `a stored participant answers for themselves`() {
        val stored = ActiveParticipant.Participant("ana")

        assertEquals(stored, ActiveParticipant.resolve(stored, listOf(ana, bruno)))
        assertEquals("ana", stored.participantId)
    }

    @Test
    fun `a participant who has left the group reads as unanswered again`() {
        assertNull(ActiveParticipant.resolve(ActiveParticipant.Participant("chloe"), listOf(ana, bruno)))
    }

    @Test
    fun `nobody is an answer that survives an empty group`() {
        assertEquals(
            ActiveParticipant.Nobody,
            ActiveParticipant.resolve(ActiveParticipant.Nobody, emptyList()),
        )
        assertNull(ActiveParticipant.Nobody.participantId)
    }

    @Test
    fun `unanswered stays unanswered`() {
        assertNull(ActiveParticipant.resolve(null, listOf(ana)))
    }
}
