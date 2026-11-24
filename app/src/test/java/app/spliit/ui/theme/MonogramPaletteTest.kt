package app.spliit.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A monogram colour that moves is worse than no colour at all: the whole point is that you
 * learn to recognise someone by it. These pin the two ways it could move — between launches,
 * and between an old build and a new one.
 */
class MonogramPaletteTest {

    @Test
    fun `every index lands inside the palette`() {
        val seeds = listOf(
            "",
            "a",
            "clx1v9m2h0000abcd",
            "🙂",
            "x".repeat(500),
        )
        for (seed in seeds) {
            val index = MonogramPalette.indexFor(seed)
            assertTrue("$index out of range for $seed", index >= 0)
            assertTrue("$index out of range for $seed", index < MonogramPalette.COUNT)
        }
    }

    @Test
    fun `the same id always gives the same colour`() {
        val seed = "clx1v9m2h0000abcd"
        val first = MonogramPalette.indexFor(seed)

        assertEquals(first, MonogramPalette.indexFor(seed))
        assertEquals(first, MonogramPalette.indexFor(seed))
    }

    /**
     * The values themselves, not just their stability. A per-process hash would pass the test
     * above and still hand out different colours after a relaunch — only checked-in
     * expectations catch a hash being swapped for one that reshuffles everyone.
     */
    @Test
    fun `the mapping is the one that shipped`() {
        assertEquals(0, MonogramPalette.indexFor("participant-1"))
        assertEquals(1, MonogramPalette.indexFor("participant-2"))
        assertEquals(5, MonogramPalette.indexFor("ana"))
        assertEquals(7, MonogramPalette.indexFor("bruno"))
    }

    @Test
    fun `different participants mostly get different colours`() {
        val indices = (0 until 200).map { MonogramPalette.indexFor("participant-$it") }.toSet()
        assertEquals(MonogramPalette.COUNT, indices.size)
    }

    /**
     * The reason positions exist: a group of four hashed into eight buckets collides more often
     * than not, and two people in one group sharing a colour is the failure the colour was
     * added to prevent.
     */
    @Test
    fun `everyone in a group up to the palette size gets their own colour`() {
        val indices = (0 until MonogramPalette.COUNT).map { MonogramPalette.indexAtPosition(it) }
        assertEquals(MonogramPalette.COUNT, indices.toSet().size)
    }

    @Test
    fun `positions past the palette wrap instead of running off it`() {
        assertEquals(0, MonogramPalette.indexAtPosition(MonogramPalette.COUNT))
        assertEquals(3, MonogramPalette.indexAtPosition(MonogramPalette.COUNT + 3))
        // Defensive: a negative position should still land on a real colour.
        assertEquals(MonogramPalette.COUNT - 1, MonogramPalette.indexAtPosition(-1))
    }

    @Test
    fun `initials come from the first two words`() {
        assertEquals("SC", MonogramPalette.initials("Sébastien Castiel"))
        assertEquals("J", MonogramPalette.initials("Jane"))
        assertEquals("AM", MonogramPalette.initials("ana maria silva"))
        assertEquals("PN", MonogramPalette.initials("  padded   name  "))
    }

    @Test
    fun `a nameless participant gets a blank chip, not a placeholder`() {
        assertEquals("", MonogramPalette.initials(""))
        assertEquals("", MonogramPalette.initials("   "))
    }
}
