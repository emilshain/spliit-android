package app.spliit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseSearchMatchTest {

    @Test
    fun `a title containing the query matched on the title`() {
        assertEquals(ExpenseSearchMatch.TITLE, ExpenseSearchMatch.of("Pizza night", "pizza"))
        assertEquals(ExpenseSearchMatch.TITLE, ExpenseSearchMatch.of("Pizza night", "ZZA NI"))
    }

    /** spliit#660's own case: the server found it, and the title is not why. */
    @Test
    fun `a title without the query matched on the notes`() {
        assertEquals(ExpenseSearchMatch.NOTES, ExpenseSearchMatch.of("Dinner", "lake house"))
    }

    /** The comparison folds case and nothing else, exactly like the server's `ILIKE`. */
    @Test
    fun `accents are not folded`() {
        assertEquals(ExpenseSearchMatch.NOTES, ExpenseSearchMatch.of("Café", "cafe"))
        assertEquals(ExpenseSearchMatch.TITLE, ExpenseSearchMatch.of("Café", "CAFÉ"))
    }
}
