package app.spliit.data.local

import app.spliit.data.model.SplitMode
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The stored list is the only way back to a group, so its shape is pinned here: what is written,
 * what a file written by an older version reads as, and what happens to a row that cannot be
 * read at all.
 */
class RecentGroupCodecTest {

    private val opened = Instant.parse("2026-08-29T13:12:42.614Z")
    private val changed = Instant.parse("2026-08-30T09:00:00Z")

    @Test
    fun `everything survives a round trip`() {
        val group = RecentGroup(
            groupId = "g1",
            groupName = "Weekend in Lisbon",
            isStarred = true,
            isArchived = false,
            activeParticipant = ActiveParticipant.Participant("ana"),
            defaultSplit = DefaultSplit(SplitMode.BY_PERCENTAGE, mapOf("ana" to 7000, "bruno" to 3000)),
            updatedAt = changed,
            lastOpenedAt = opened,
            instanceURL = "https://spliit.app",
        )

        assertEquals(listOf(group), RecentGroupsCodec.decode(RecentGroupsCodec.encode(listOf(group))))
    }

    @Test
    fun `the written keys are the ones the old app used`() {
        val json = RecentGroupsCodec.encode(listOf(RecentGroup("g1", "Flat", isStarred = true)))
        val row = RecentGroupsCodec.json.parseToJsonElement(json).jsonArray.single().jsonObject

        assertEquals("g1", row["groupId"]?.jsonPrimitive?.content)
        assertEquals("Flat", row["groupName"]?.jsonPrimitive?.content)
        assertEquals("true", row["isStarred"]?.jsonPrimitive?.content)
        assertEquals("false", row["isArchived"]?.jsonPrimitive?.content)
        // Nil optionals are omitted, as Swift's synthesized encoding does.
        assertTrue("unexpected keys: ${row.keys}", "activeParticipant" !in row)
        assertTrue("unexpected keys: ${row.keys}", "instanceURL" !in row)
    }

    /**
     * A list written before any of the flags existed — by the React Native app, or by an older
     * build of this one. Every missing key has to fall back rather than throw.
     */
    @Test
    fun `a legacy row without flags decodes with defaults`() {
        val legacy = """[{"groupId":"g1","groupName":"Flat"}]"""

        val group = RecentGroupsCodec.decode(legacy).single()

        assertEquals("g1", group.groupId)
        assertEquals("Flat", group.groupName)
        assertEquals(false, group.isStarred)
        assertEquals(false, group.isArchived)
        assertNull(group.activeParticipant)
        assertNull(group.defaultSplit)
        assertNull(group.instanceURL)
        assertNull(group.lastOpenedAt)
    }

    @Test
    fun `a legacy row could still say who the user was`() {
        val legacy = """[{"groupId":"g1","groupName":"Flat","activeParticipant":"ana"}]"""

        assertEquals(
            ActiveParticipant.Participant("ana"),
            RecentGroupsCodec.decode(legacy).single().activeParticipant,
        )
    }

    @Test
    fun `an unknown split mode costs the split, not the group`() {
        val stored = """
            [{"groupId":"g1","groupName":"Flat",
              "defaultSplit":{"splitMode":"BY_WEIGHT","shares":{"ana":100}}}]
        """.trimIndent()

        val group = RecentGroupsCodec.decode(stored).single()

        assertEquals("g1", group.groupId)
        assertNull(group.defaultSplit)
    }

    @Test
    fun `one unreadable row does not take the list with it`() {
        val stored = """[{"groupName":"no id"},{"groupId":"g2","groupName":"Trip"}]"""

        val groups = RecentGroupsCodec.decode(stored)

        assertEquals(1, groups.size)
        assertEquals("g2", groups.single().groupId)
    }

    @Test
    fun `nothing stored reads as nothing`() {
        assertTrue(RecentGroupsCodec.decode(null).isEmpty())
        assertTrue(RecentGroupsCodec.decode("").isEmpty())
        assertTrue(RecentGroupsCodec.decode("not json").isEmpty())
    }

    @Test
    fun `an amount split remembers only its mode`() {
        val group = RecentGroup(
            groupId = "g1",
            groupName = "Trip",
            defaultSplit = DefaultSplit(SplitMode.BY_AMOUNT),
        )

        assertNull(RecentGroupsCodec.decode(RecentGroupsCodec.encode(listOf(group))).single().defaultSplit?.shares)
    }
}
