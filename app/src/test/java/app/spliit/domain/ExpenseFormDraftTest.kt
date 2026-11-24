package app.spliit.domain

import app.spliit.data.local.DefaultSplit
import app.spliit.data.local.RecentGroupsLogic
import app.spliit.data.model.ExpenseCategory
import app.spliit.data.model.ExpenseDetails
import app.spliit.data.model.ExpenseListItem
import app.spliit.data.model.Group
import app.spliit.data.model.Participant
import app.spliit.data.model.Reimbursement
import app.spliit.data.model.SplitMode
import app.spliit.data.remote.ExpenseFormValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * The expense form's rules, which are the ones that decide whether money is written down
 * correctly: who is in the split, what their share is on the wire, and when the whole thing is
 * refused.
 */
class ExpenseFormDraftTest {

    private val locale = Locale.US

    private val group = Group(
        id = "g1",
        name = "Flat",
        currency = "€",
        currencyCode = "EUR",
        createdAt = Instant.parse("2026-01-05T10:00:00Z"),
        participants = listOf(
            Participant("p1", "Ana"),
            Participant("p2", "Bruno"),
            Participant("p3", "Cara"),
        ),
    )

    private fun draft(
        amount: String = "30.00",
        split: SplitMode = SplitMode.EVENLY,
        participants: List<ParticipantShare> = group.participants.map {
            ParticipantShare(it.id, it.name, isIncluded = true)
        },
    ) = ExpenseFormDraft(
        title = "Dinner",
        expenseDate = LocalDate.of(2026, 8, 12),
        amountText = amount,
        paidById = "p1",
        splitMode = split,
        participants = participants,
        locale = locale,
        minorUnitDigits = 2,
        groupCurrencyCode = "EUR",
        originalCurrencyCode = "EUR",
    )

    // MARK: - Creating and defaults

    @Test
    fun `a new expense covers the group, split evenly, paid by the first participant`() {
        val created = ExpenseFormDraft.creatingIn(group, locale = locale)

        assertEquals(SplitMode.EVENLY, created.splitMode)
        assertEquals("p1", created.paidById)
        assertEquals(3, created.includedParticipants.size)
        assertTrue(created.problems.contains(ExpenseFormDraft.Problem.AmountMissing))
    }

    @Test
    fun `a new expense is paid by whoever the user said they are`() {
        assertEquals("p2", ExpenseFormDraft.creatingIn(group, paidBy = "p2", locale = locale).paidById)
    }

    @Test
    fun `a remembered split becomes the starting point`() {
        val split = DefaultSplit(SplitMode.BY_PERCENTAGE, mapOf("p1" to 7000, "p2" to 3000))

        val created = ExpenseFormDraft.creatingIn(group, defaultSplit = split, locale = locale)

        assertEquals(SplitMode.BY_PERCENTAGE, created.splitMode)
        assertEquals(listOf("p1", "p2"), created.includedParticipants.map { it.id })
        assertEquals("70", created.participants.first { it.id == "p1" }.valueText)
        assertEquals("30", created.participants.first { it.id == "p2" }.valueText)
    }

    @Test
    fun `a split naming somebody who left is ignored whole`() {
        val stale = DefaultSplit(SplitMode.BY_PERCENTAGE, mapOf("p1" to 7000, "gone" to 3000))

        val created = ExpenseFormDraft.creatingIn(group, defaultSplit = stale, locale = locale)

        assertEquals(SplitMode.EVENLY, created.splitMode)
        assertEquals(3, created.includedParticipants.size)
    }

    @Test
    fun `a by-amount default remembers only its mode`() {
        val split = DefaultSplit(SplitMode.BY_AMOUNT, null)

        val created = ExpenseFormDraft.creatingIn(group, defaultSplit = split, locale = locale)

        assertEquals(SplitMode.BY_AMOUNT, created.splitMode)
        assertEquals(3, created.includedParticipants.size)
        assertTrue(created.participants.all { it.valueText == "1" })
    }

    // MARK: - Validation

    @Test
    fun `a title needs two characters`() {
        assertTrue(draft().copy(title = " a ").problems.contains(ExpenseFormDraft.Problem.TitleTooShort))
        assertFalse(draft().copy(title = "ab").problems.contains(ExpenseFormDraft.Problem.TitleTooShort))
    }

    @Test
    fun `an amount is required, non-zero, a number, and under the ceiling`() {
        assertTrue(draft(amount = "").problems.contains(ExpenseFormDraft.Problem.AmountMissing))
        assertTrue(draft(amount = "0").problems.contains(ExpenseFormDraft.Problem.AmountZero))
        assertTrue(draft(amount = "abc").problems.contains(ExpenseFormDraft.Problem.AmountNotANumber))
        assertTrue(
            draft(amount = "10000000.01").problems.contains(ExpenseFormDraft.Problem.AmountTooLarge),
        )
        assertTrue(draft(amount = "10000000").isValid)
    }

    @Test
    fun `an expense needs a payer and somebody to pay for`() {
        assertTrue(draft().copy(paidById = null).problems.contains(ExpenseFormDraft.Problem.PayerMissing))

        val nobody = group.participants.map { ParticipantShare(it.id, it.name, isIncluded = false) }
        assertTrue(
            draft(participants = nobody).problems.contains(ExpenseFormDraft.Problem.NoParticipantsSelected),
        )
    }

    @Test
    fun `a share must be a positive number in every mode but evenly`() {
        val shares = listOf(
            ParticipantShare("p1", "Ana", isIncluded = true, valueText = "0"),
            ParticipantShare("p2", "Bruno", isIncluded = true, valueText = "abc"),
        )

        val problems = draft(split = SplitMode.BY_SHARES, participants = shares).problems

        assertTrue(problems.contains(ExpenseFormDraft.Problem.ShareNotPositive("p1")))
        assertTrue(problems.contains(ExpenseFormDraft.Problem.ShareNotANumber("p2")))
    }

    @Test
    fun `a percentage split has to add up to a hundred`() {
        val sixty = listOf(
            ParticipantShare("p1", "Ana", isIncluded = true, valueText = "60"),
            ParticipantShare("p2", "Bruno", isIncluded = true, valueText = "30"),
        )
        val hundred = sixty.map { it.copy(valueText = if (it.id == "p2") "40" else it.valueText) }

        assertEquals(
            listOf(ExpenseFormDraft.Problem.PercentagesDoNotSumTo100(1000)),
            draft(split = SplitMode.BY_PERCENTAGE, participants = sixty).problems
                .filterIsInstance<ExpenseFormDraft.Problem.PercentagesDoNotSumTo100>(),
        )
        assertEquals(1000, draft(split = SplitMode.BY_PERCENTAGE, participants = sixty).unallocated)
        assertTrue(draft(split = SplitMode.BY_PERCENTAGE, participants = hundred).isValid)
    }

    @Test
    fun `an amount split has to add up to the total`() {
        val short = listOf(
            ParticipantShare("p1", "Ana", isIncluded = true, valueText = "10"),
            ParticipantShare("p2", "Bruno", isIncluded = true, valueText = "5"),
        )

        val problems = draft(split = SplitMode.BY_AMOUNT, participants = short).problems

        assertTrue(problems.contains(ExpenseFormDraft.Problem.AmountsDoNotSumToTotal(1500)))
        assertEquals(1500, draft(split = SplitMode.BY_AMOUNT, participants = short).unallocated)

        val exact = short.map { it.copy(valueText = if (it.id == "p2") "20" else it.valueText) }
        assertTrue(draft(split = SplitMode.BY_AMOUNT, participants = exact).isValid)
    }

    @Test
    fun `shares are not required to sum under a share-count split`() {
        val shares = listOf(
            ParticipantShare("p1", "Ana", isIncluded = true, valueText = "2"),
            ParticipantShare("p2", "Bruno", isIncluded = true, valueText = "1"),
        )

        assertTrue(draft(split = SplitMode.BY_SHARES, participants = shares).isValid)
        assertNull(draft(split = SplitMode.BY_SHARES, participants = shares).unallocated)
    }

    // MARK: - The wire

    @Test
    fun `share values are scaled by a hundred, except by-amount which is money`() {
        val share = ParticipantShare("p1", "Ana", isIncluded = true, valueText = "2.5")
        val amount = share.copy(valueText = "12.40")

        assertEquals(100, draft().shareValue(share))
        assertEquals(250, draft(split = SplitMode.BY_SHARES, participants = listOf(share)).shareValue(share))
        assertEquals(250, draft(split = SplitMode.BY_PERCENTAGE, participants = listOf(share)).shareValue(share))
        assertEquals(1240, draft(split = SplitMode.BY_AMOUNT, participants = listOf(amount)).shareValue(amount))
    }

    @Test
    fun `in a yen group shares of two are still two hundred`() {
        val created = ExpenseFormDraft.creatingIn(group, locale = locale)
            .copy(minorUnitDigits = 0, amountText = "1234", splitMode = SplitMode.BY_SHARES)
            .let { it.copy(participants = it.participants.map { p -> p.copy(valueText = "2") }) }

        assertEquals(200, created.shareValue(created.participants.first()))
    }

    @Test
    fun `the payload carries the region split, trimmed notes and no conversion`() {
        val values = draft(split = SplitMode.BY_PERCENTAGE, participants = listOf(
            ParticipantShare("p1", "Ana", isIncluded = true, valueText = "60"),
            ParticipantShare("p2", "Bruno", isIncluded = true, valueText = "40"),
            ParticipantShare("p3", "Cara", isIncluded = false, valueText = "1"),
        )).copy(notes = "  dinner  ").formValues

        assertNotNull(values)
        values!!
        assertEquals("Dinner", values.title)
        assertEquals(3000, values.amount)
        assertEquals(Instant.parse("2026-08-12T00:00:00Z"), values.expenseDate)
        assertEquals(SplitMode.BY_PERCENTAGE, values.splitMode)
        assertEquals(listOf("p1", "p2"), values.paidFor.map { it.participant })
        assertEquals(listOf(6000, 4000), values.paidFor.map { it.shares })
        assertEquals("dinner", values.notes)
        assertNull(values.originalCurrency)
        assertNull(values.originalAmount)
    }

    @Test
    fun `an invalid draft has no payload at all`() {
        assertNull(draft(amount = "").formValues)
        assertNull(draft(split = SplitMode.BY_PERCENTAGE).formValues)
    }

    @Test
    fun `the split's amounts match the apportionment, odd cent and all`() {
        val created = draft(amount = "10.00", split = SplitMode.BY_SHARES, participants = listOf(
            ParticipantShare("p1", "Ana", isIncluded = true, valueText = "1"),
            ParticipantShare("p2", "Bruno", isIncluded = true, valueText = "1"),
            ParticipantShare("p3", "Cara", isIncluded = true, valueText = "1"),
        ))

        val amounts = created.shareAmounts(expenseId = "expense-1")!!

        assertEquals(1000, amounts.values.sum())
        val expected = ExpenseShares.shares(
            expenseId = "expense-1",
            amount = 1000,
            splitMode = SplitMode.BY_SHARES,
            paidFor = listOf(
                listOf("p1" to 100, "p2" to 100, "p3" to 100).map { (id, shares) ->
                    object : app.spliit.data.model.ExpenseShareRow {
                        override val participantId = id
                        override val shares = shares
                    }
                },
            ).first(),
        )
        assertEquals(expected, amounts)
    }

    @Test
    fun `a share amount is only shown for a split that could be saved`() {
        assertTrue(draft(amount = "10.00").showsShareAmounts)

        val over = draft(split = SplitMode.BY_PERCENTAGE, participants = listOf(
            ParticipantShare("p1", "Ana", isIncluded = true, valueText = "60"),
            ParticipantShare("p2", "Bruno", isIncluded = true, valueText = "60"),
        ))
        assertFalse(over.showsShareAmounts)
        assertFalse(draft(split = SplitMode.BY_AMOUNT, participants = emptyList()).showsShareAmounts)
    }

    // MARK: - Editing and settling

    @Test
    fun `editing reads the stored shares back into the fields they came from`() {
        val expense = ExpenseDetails(
            id = "e1",
            groupId = "g1",
            title = "Taxi",
            amount = 2400,
            categoryId = 3,
            expenseDate = Instant.parse("2026-07-01T00:00:00Z"),
            createdAt = Instant.parse("2026-07-01T09:00:00Z"),
            paidById = "p2",
            paidBy = Participant("p2", "Bruno"),
            paidFor = listOf(
                ExpenseDetails.PaidFor("p1", 7000),
                ExpenseDetails.PaidFor("p2", 3000),
            ),
            isReimbursement = false,
            splitMode = SplitMode.BY_PERCENTAGE,
            notes = "from the station",
        )

        val editing = ExpenseFormDraft.editing(expense, group, locale)

        assertEquals("Taxi", editing.title)
        assertEquals("24.00", editing.amountText)
        assertEquals(LocalDate.of(2026, 7, 1), editing.expenseDate)
        assertEquals("p2", editing.paidById)
        assertEquals(SplitMode.BY_PERCENTAGE, editing.splitMode)
        assertEquals(listOf("p1", "p2"), editing.includedParticipants.map { it.id })
        assertEquals("70", editing.participants.first { it.id == "p1" }.valueText)
        assertTrue(editing.isValid)
    }

    @Test
    fun `settling a reimbursement prefills who paid whom`() {
        val draft = ExpenseFormDraft.settling(
            Reimbursement(from = "p2", to = "p1", amount = 1250),
            group,
            title = "Payment from Bruno to Ana",
            locale = locale,
        )

        assertEquals("12.50", draft.amountText)
        assertEquals("p2", draft.paidById)
        assertEquals(1, draft.categoryId)
        assertTrue(draft.isReimbursement)
        assertEquals(listOf("p1"), draft.includedParticipants.map { it.id })
        assertTrue(draft.isValid)
        assertFalse(draft.isSplitWorthRemembering)
    }

    // MARK: - Currency conversion

    private fun converted() = draft(amount = "").copy(
        originalCurrencyCode = "USD",
        originalAmountText = "40.00",
        conversionRateText = "0.92",
    )

    @Test
    fun `a conversion derives the total from what was paid and the rate`() {
        val draft = converted()

        assertTrue(draft.conversionRequired)
        assertEquals(4000, draft.originalAmountMinorUnits)
        // 40.00 USD at 0.92 EUR each is 36.80 EUR.
        assertEquals(3680, draft.amountMinorUnits)
        assertTrue(draft.isValid)
    }

    @Test
    fun `a conversion without a rate is refused`() {
        val draft = converted().copy(conversionRateText = "")

        assertTrue(draft.problems.contains(ExpenseFormDraft.Problem.ConversionRateMissing))
        assertNull(draft.amountMinorUnits)
    }

    @Test
    fun `a rate of zero is refused`() {
        val draft = converted().copy(conversionRateText = "0")

        assertTrue(draft.problems.contains(ExpenseFormDraft.Problem.ConversionRateNotPositive))
    }

    @Test
    fun `coming back to the group's currency keeps the converted total`() {
        val back = converted().useCurrency("EUR")

        assertFalse(back.conversionRequired)
        assertEquals("36.80", back.amountText)
        assertEquals("", back.conversionRateText)
    }

    @Test
    fun `a conversion is only possible when both sides have an ISO code`() {
        val symbolOnly = draft().copy(groupCurrencyCode = null, originalCurrencyCode = "USD")

        assertFalse(symbolOnly.conversionRequired)
        assertEquals(3000, symbolOnly.amountMinorUnits)
    }

    @Test
    fun `the payload sends the conversion only while it is in force`() {
        val values = converted().formValues

        assertNotNull(values)
        assertEquals(3680, values!!.amount)
        assertEquals(4000, values.originalAmount)
        assertEquals("USD", values.originalCurrency)
        assertEquals(0, BigDecimal("0.92").compareTo(values.conversionRate))
    }

    // MARK: - Remembering the split

    @Test
    fun `an even split of the whole group is remembered as the group itself`() {
        val values = draft(amount = "30.00").formValues!!

        val remembered = DefaultSplit.remembering(values, group.participants)

        assertEquals(SplitMode.EVENLY, remembered.splitMode)
        assertNull(remembered.shares)
        assertTrue(remembered.appliesTo(group.participants.plus(Participant("p4", "Dan"))))
    }

    @Test
    fun `a share split is remembered with its names`() {
        val values = draft(
            amount = "30.00",
            split = SplitMode.BY_SHARES,
            participants = listOf(
                ParticipantShare("p1", "Ana", isIncluded = true, valueText = "2"),
                ParticipantShare("p2", "Bruno", isIncluded = true, valueText = "1"),
            ),
        ).formValues!!

        val remembered = DefaultSplit.remembering(values, group.participants)

        assertEquals(SplitMode.BY_SHARES, remembered.splitMode)
        assertEquals(mapOf("p1" to 200, "p2" to 100), remembered.shares)
        assertFalse(remembered.appliesTo(listOf(Participant("p2", "Bruno"))))
    }

    @Test
    fun `a by-amount split is remembered as its mode alone`() {
        val values = draft(
            amount = "30.00",
            split = SplitMode.BY_AMOUNT,
            participants = listOf(
                ParticipantShare("p1", "Ana", isIncluded = true, valueText = "10"),
                ParticipantShare("p2", "Bruno", isIncluded = true, valueText = "20"),
            ),
        ).formValues!!

        val remembered = DefaultSplit.remembering(values, group.participants)

        assertEquals(SplitMode.BY_AMOUNT, remembered.splitMode)
        assertNull(remembered.shares)
    }

    @Test
    fun `a remembered split survives the store's own rules`() {
        val values = draft(amount = "30.00").formValues!!
        val remembered = DefaultSplit.remembering(values, group.participants)
        val groups = listOf(
            app.spliit.data.local.RecentGroup(groupId = "g1", groupName = "Flat"),
        )

        val stored = RecentGroupsLogic.setDefaultSplit(
            groups, "g1", remembered, Instant.parse("2026-08-12T10:00:00Z"),
        )

        assertEquals(remembered, stored.first().defaultSplit)
    }
}
