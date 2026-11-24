package app.spliit.domain

import app.spliit.data.model.Group
import app.spliit.data.model.Participant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale

class GroupFormDraftTest {

    private fun draft(
        name: String = "Weekend in Lisbon",
        currency: String = "€",
        participants: List<String> = listOf("Ana", "Bruno"),
    ) = GroupFormDraft(
        name = name,
        currency = currency,
        participants = participants.map { ParticipantDraft(name = it) },
    )

    @Test
    fun `a complete group is valid`() {
        assertTrue(draft().isValid)
    }

    @Test
    fun `the name must be between 2 and 50 characters`() {
        assertTrue(draft(name = "A").problems.contains(GroupFormDraft.Problem.NameTooShort))
        assertTrue(draft(name = "  A  ").problems.contains(GroupFormDraft.Problem.NameTooShort))
        assertTrue(
            draft(name = "a".repeat(51)).problems.contains(GroupFormDraft.Problem.NameTooLong),
        )
        assertTrue(draft(name = "a".repeat(50)).isValid)
    }

    @Test
    fun `the currency symbol must be between 1 and 5 characters`() {
        assertTrue(draft(currency = "").problems.contains(GroupFormDraft.Problem.CurrencyMissing))
        assertTrue(draft(currency = "   ").problems.contains(GroupFormDraft.Problem.CurrencyMissing))
        assertTrue(
            draft(currency = "ABCDEF").problems.contains(GroupFormDraft.Problem.CurrencyTooLong),
        )
        assertTrue(draft(currency = "CHF").isValid)
    }

    @Test
    fun `a group needs at least one participant`() {
        assertTrue(
            draft(participants = emptyList()).problems.contains(GroupFormDraft.Problem.NoParticipants),
        )
    }

    @Test
    fun `participant names follow the same length rules`() {
        val form = draft(participants = listOf("A", "Bruno"))
        val target = form.participants[0].id

        assertTrue(
            form.problems.contains(GroupFormDraft.Problem.ParticipantNameTooShort(target)),
        )
        assertEquals(1, form.problemsForParticipant(target).size)
        assertTrue(form.problemsForParticipant(form.participants[1].id).isEmpty())
    }

    /** The server flags the later of a duplicate pair, so errors land where a user expects. */
    @Test
    fun `a duplicate name is flagged on the second occurrence only`() {
        val form = draft(participants = listOf("Ana", "Bruno", "Ana"))

        assertTrue(
            form.problems.contains(
                GroupFormDraft.Problem.DuplicateParticipantName(form.participants[2].id),
            ),
        )
        assertTrue(form.problemsForParticipant(form.participants[0].id).isEmpty())
    }

    @Test
    fun `names differing only by surrounding spaces still count as duplicates`() {
        val form = draft(participants = listOf("Ana", "  Ana  "))

        assertTrue(
            form.problems.contains(
                GroupFormDraft.Problem.DuplicateParticipantName(form.participants[1].id),
            ),
        )
    }

    @Test
    fun `submitted values are trimmed, and blank information is omitted`() {
        val form = draft(name = "  Lisbon trip  ", participants = listOf("  Ana  ", "Bruno"))
            .copy(information = "   ")

        val values = form.formValues

        assertEquals("Lisbon trip", values.name)
        assertEquals(listOf("Ana", "Bruno"), values.participants.map { it.name })
        assertNull(values.information)
    }

    @Test
    fun `editing an existing group keeps participant IDs and the ISO currency code`() {
        val group = Group(
            id = "g1",
            name = "Flat 3B",
            information = "Rent and bills",
            currency = "$",
            currencyCode = "USD",
            createdAt = Instant.now(),
            participants = listOf(Participant("p1", "Dana"), Participant("p2", "Eli")),
        )

        val form = GroupFormDraft.editing(group).copy(
            participants = GroupFormDraft.editing(group).participants + ParticipantDraft(name = "Fen"),
        )
        val values = form.formValues

        assertEquals("USD", values.currencyCode)
        assertEquals(listOf("p1", "p2", null), values.participants.map { it.id })
        assertEquals("Rent and bills", values.information)
    }

    // MARK: - Currency

    @Test
    fun `a new group starts in the currency the device is set to`() {
        val swiss = GroupFormDraft.newGroup(Locale.forLanguageTag("de-CH"))
        assertEquals("CHF", swiss.currencyCode)
        assertEquals("CHF", swiss.currency)

        val american = GroupFormDraft.newGroup(Locale.forLanguageTag("en-US"))
        assertEquals("USD", american.currencyCode)
        assertEquals("$", american.currency)
    }

    /** The symbol beside every amount and the code the amounts are in have to agree. */
    @Test
    fun `picking a currency sets the symbol as well as the code`() {
        val chf = requireNotNull(CurrencyCatalog.named("CHF", Locale.US))
        val form = draft(currency = "$").use(chf)

        assertEquals("CHF", form.currencyCode)
        assertEquals("CHF", form.currency)
        assertEquals("CHF", form.formValues.currencyCode)
        assertTrue(!form.usesCustomSymbol)
    }

    @Test
    fun `a custom symbol keeps the symbol and drops the code`() {
        val eur = requireNotNull(CurrencyCatalog.named("EUR", Locale.US))
        val form = draft().use(eur).useCustomSymbol()

        assertTrue(form.usesCustomSymbol)
        assertEquals("The symbol that was there stays.", "€", form.currency)
        assertEquals(
            "Empty, not null: null is omitted from the request and leaves the stored code alone.",
            "",
            form.formValues.currencyCode,
        )
    }

    /** The web app writes an empty string where we write null. */
    @Test
    fun `an empty code from the web is treated as no code at all`() {
        val form = draft().copy(currencyCode = "")

        assertTrue(form.usesCustomSymbol)
        assertTrue(form.isValid)
        assertEquals("", form.formValues.currencyCode)
    }

    @Test
    fun `a code that isn't three letters is reported rather than sent`() {
        val form = draft().copy(currencyCode = "DOLLARS")

        assertTrue(form.problems.contains(GroupFormDraft.Problem.CurrencyCodeInvalid))
    }
}
