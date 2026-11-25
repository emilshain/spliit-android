package app.spliit.domain

import app.spliit.data.local.DefaultSplit
import app.spliit.data.model.ExpenseDetails
import app.spliit.data.model.ExpenseDocument
import app.spliit.data.model.ExpenseShareRow
import app.spliit.data.model.Group
import app.spliit.data.model.Reimbursement
import app.spliit.data.model.RecurrenceRule
import app.spliit.data.model.SplitMode
import app.spliit.data.remote.ExpenseFormValues
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

/** One participant's row in the "Paid for" list. */
data class ParticipantShare(
    /** The server-side participant ID. */
    val id: String,
    val name: String,
    val isIncluded: Boolean = false,
    /**
     * What the user typed: a share count, a percentage, or an amount, depending on the split
     * mode. Unused when splitting evenly.
     */
    val valueText: String = "1",
)

/**
 * The expense form's state, plus the validation the server would apply.
 *
 * Mirrors `expenseFormSchema` in the web app, including the split-mode sum rules that are the
 * easiest thing to get subtly wrong.
 */
data class ExpenseFormDraft(
    val title: String = "",
    val expenseDate: LocalDate = CalendarDay.today(),
    /** The total, as typed. */
    val amountText: String = "",
    val categoryId: Int = 0,
    val paidById: String? = null,
    val splitMode: SplitMode = SplitMode.EVENLY,
    /**
     * An amount added on top of the typed amounts and shared equally, available in
     * [SplitMode.BY_AMOUNT] — the one mode where nobody can see where a total came from.
     *
     * It is never sent anywhere: the total grows by it and each participant's share grows by
     * their part of it, so the expense the server receives is an ordinary BY_AMOUNT expense whose
     * shares add up to its amount. Nothing about the stored format changes.
     */
    val taxText: String = "",
    val participants: List<ParticipantShare> = emptyList(),
    /**
     * Whether this split should become the one the group's next expense starts from.
     *
     * Always starts off, editing included: remembering a split is something to ask for, and a box
     * that arrives ticked would rewrite the default every time somebody corrected a typo.
     */
    val saveSplitAsDefault: Boolean = false,
    val isReimbursement: Boolean = false,
    val notes: String = "",
    /** Used to parse typed numbers; a comma is the decimal separator in much of the world. */
    val locale: Locale = Locale.getDefault(),
    /** How many digits the group's currency keeps behind the decimal point. */
    val minorUnitDigits: Int = 2,
    /** Preserved across an edit so saving from the app doesn't drop what the web app set. */
    val recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
    val documents: List<ExpenseDocument> = emptyList(),
    /** The group's ISO code, or null when it has only a free-text symbol. */
    val groupCurrencyCode: String? = null,
    /** What this expense was actually paid in. Starts as the group's own currency. */
    val originalCurrencyCode: String? = null,
    val originalAmountText: String = "",
    /** One unit of [originalCurrencyCode] in the group's currency. A rate, not money. */
    val conversionRateText: String = "",
) {

    // MARK: - Derived values

    /**
     * The total in minor units, or null if what was typed isn't a number.
     *
     * Under a conversion the total is not typed at all: it is what the amount paid comes to at
     * the rate given, and deriving it here rather than copying it into [amountText] is what makes
     * it impossible to store an expense whose rate does not produce its own amount.
     */
    val amountMinorUnits: Int?
        get() = if (conversionRequired) {
            convertedAmountMinorUnits
        } else {
            MoneyFormatter.minorUnits(amountText, locale, minorUnitDigits)
        }

    // MARK: - Currency conversion

    /**
     * Whether this expense was paid in a currency the group is not denominated in.
     *
     * Both sides have to be real ISO codes: a group with only a free-text symbol has nothing to
     * convert *to*.
     */
    val conversionRequired: Boolean
        get() {
            val group = isoCode(groupCurrencyCode) ?: return false
            val original = isoCode(originalCurrencyCode) ?: return false
            return group != original
        }

    /** Digits in the minor unit of what was actually paid, which is not the group's. */
    val originalMinorUnitDigits: Int
        get() = MoneyFormatter.minorUnitDigitsFor(isoCode(originalCurrencyCode), locale)

    /** What was paid, in the minor units of the currency it was paid in. */
    val originalAmountMinorUnits: Int?
        get() = MoneyFormatter.minorUnits(originalAmountText, locale, originalMinorUnitDigits)

    val conversionRate: BigDecimal?
        get() = MoneyFormatter.number(conversionRateText, locale)

    /** What the amount paid comes to in the group's currency, in its minor units. */
    val convertedAmountMinorUnits: Int?
        get() {
            if (!conversionRequired) return null
            val paid = originalAmountMinorUnits ?: return null
            val rate = conversionRate ?: return null

            val paidInMajor = BigDecimal.valueOf(paid.toLong()).movePointLeft(originalMinorUnitDigits)
            val converted = paidInMajor
                .multiply(rate)
                .movePointRight(minorUnitDigits)
            // A rate small enough to round a real payment down to nothing is still a total of
            // zero, which validation refuses — rather than an exception here.
            return runCatching { converted.setScale(0, RoundingMode.HALF_UP).toInt() }.getOrNull()
        }

    /**
     * Changing what the expense was paid in.
     *
     * Coming back to the group's own currency carries the converted total into the amount field,
     * so the expense keeps the value it was showing rather than snapping back to whatever had
     * been typed before the conversion.
     */
    fun useCurrency(code: String?): ExpenseFormDraft {
        val converted = convertedAmountMinorUnits
        val isADifferentCurrency = isoCode(code) != isoCode(originalCurrencyCode)
        var next = copy(originalCurrencyCode = code)
        if (isADifferentCurrency) {
            // A rate belongs to a pair of currencies, so it cannot come along to another pair.
            next = next.copy(conversionRateText = "")
        }
        if (!next.conversionRequired && converted != null) {
            next = next.copy(
                amountText = text(value = converted, locale = locale, minorUnitDigits = minorUnitDigits),
            )
        }
        return next
    }

    /** Takes a looked-up rate as the one to use. */
    fun use(rate: BigDecimal): ExpenseFormDraft =
        copy(conversionRateText = text(rate = rate, locale = locale))

    val includedParticipants: List<ParticipantShare>
        get() = participants.filter { it.isIncluded }

    /**
     * Whether keeping this split for the group's later expenses is worth offering.
     *
     * A reimbursement is one person handing money to one other, and never the shape of the
     * group's ordinary expenses.
     */
    val isSplitWorthRemembering: Boolean get() = !isReimbursement

    /** Whether the split covers the whole group. */
    val allParticipantsIncluded: Boolean
        get() = participants.isNotEmpty() && participants.all { it.isIncluded }

    /**
     * Puts the whole group in the split, or takes it all out.
     *
     * Everyone keeps the share they were last given: a value typed before an unintended "select
     * none" comes back with its owner.
     */
    fun setAllParticipantsIncluded(isIncluded: Boolean): ExpenseFormDraft =
        copy(participants = participants.map { it.copy(isIncluded = isIncluded) })

    /**
     * What one participant's `shares` field should be sent as.
     *
     * The server stores this verbatim, and its meaning changes with the split mode: a share count
     * or percentage scaled by 100, or — for [SplitMode.BY_AMOUNT] — a raw minor-unit amount.
     */
    fun shareValue(participant: ParticipantShare): Int? = when (splitMode) {
        SplitMode.EVENLY -> 100
        SplitMode.BY_SHARES, SplitMode.BY_PERCENTAGE ->
            MoneyFormatter.minorUnits(participant.valueText, locale)
        SplitMode.BY_AMOUNT ->
            MoneyFormatter.minorUnits(participant.valueText, locale, minorUnitDigits)
    }

    // MARK: - Tax, split equally

    /** The tax as typed, in minor units; null when it isn't a number. */
    val taxMinorUnits: Int?
        get() = when {
            splitMode != SplitMode.BY_AMOUNT -> null
            taxText.isBlank() -> 0
            else -> MoneyFormatter.minorUnits(taxText, locale, minorUnitDigits)
        }

    /** Whether a tax is being used at all. Blank means the form behaves exactly as before. */
    val taxApplies: Boolean
        get() = splitMode == SplitMode.BY_AMOUNT && taxText.isNotBlank()

    /** What the typed participant amounts come to, before any tax. */
    private val typedAmountsTotal: Int
        get() = includedParticipants.sumOf { shareValue(it) ?: 0 }

    /**
     * The tax each included participant carries: equal parts, with the leftover minor units
     * handed to the first of them.
     *
     * Equal parts and an exact sum, both: the remainder can't be left out (the shares must still
     * add up to the total) and it can't be held by nobody (there is no such column on the server).
     */
    private val taxShareByParticipant: Map<String, Int>
        get() {
            val tax = taxMinorUnits ?: return emptyMap()
            val included = includedParticipants
            if (!taxApplies || tax <= 0 || included.isEmpty()) return emptyMap()

            val each = tax / included.size
            val remainder = tax % included.size
            return included.mapIndexed { index, participant ->
                participant.id to each + if (index < remainder) 1 else 0
            }.toMap()
        }

    /**
     * What the server should be told this participant pays: their typed amount plus their equal
     * part of the tax. Identical to [shareValue] when no tax is in play.
     */
    fun payableShareValue(participant: ParticipantShare): Int? =
        shareValue(participant)?.let { it + (taxShareByParticipant[participant.id] ?: 0) }

    /**
     * Moves the total to what the typed amounts plus the tax come to.
     *
     * Called as the amounts are edited, so the total field can't drift away from what the shares
     * will add up to. A draft with no tax is returned untouched, which is what keeps the form
     * behaving as it always has for everyone who does not use this.
     */
    fun rebalanced(): ExpenseFormDraft {
        if (!taxApplies) return this
        val tax = taxMinorUnits ?: return this
        return copy(
            amountText = text(
                value = typedAmountsTotal + tax,
                locale = locale,
                minorUnitDigits = minorUnitDigits,
            ),
        )
    }

    /** For BY_AMOUNT, how far the shares are from the total; for BY_PERCENTAGE, from 100%. */
    val unallocated: Int?
        get() {
            val allocated = includedParticipants.sumOf { payableShareValue(it) ?: 0 }
            return when (splitMode) {
                SplitMode.BY_AMOUNT -> amountMinorUnits?.let { it - allocated }
                SplitMode.BY_PERCENTAGE -> PERCENT_SCALE - allocated
                SplitMode.EVENLY, SplitMode.BY_SHARES -> null
            }
        }

    // MARK: - What the split comes to

    /**
     * What each included participant's share comes to, in the group's minor units, by participant
     * ID — the number the balances tab will charge them once the expense is saved.
     *
     * Distinct from [shareValue], which is the share as typed, on the ×100 scale the wire uses:
     * that is what goes in, and this is the money that comes out.
     */
    fun shareAmounts(expenseId: String?): Map<String, Int>? {
        val amount = amountMinorUnits ?: return null
        val rows = includedParticipants.map { participant ->
            object : ExpenseShareRow {
                override val participantId: String = participant.id
                override val shares: Int = payableShareValue(participant) ?: 0
            }
        }
        return ExpenseShares.shares(
            expenseId = expenseId,
            amount = amount,
            splitMode = splitMode,
            paidFor = rows,
        )
    }

    /**
     * Whether what each share comes to is worth printing beside the name: only for a split that
     * would be saved as it stands.
     */
    val showsShareAmounts: Boolean
        get() {
            if (isReimbursement || splitMode == SplitMode.BY_AMOUNT) return false
            val usable = includedParticipants.all {
                splitMode == SplitMode.EVENLY || (shareValue(it) ?: 0) > 0
            }
            return usable && (unallocated ?: 0) == 0
        }

    // MARK: - Validation

    sealed interface Problem {
        data object TitleTooShort : Problem
        data object AmountMissing : Problem
        data object AmountNotANumber : Problem
        data object AmountZero : Problem
        data object AmountTooLarge : Problem
        data object OriginalAmountMissing : Problem
        data object OriginalAmountNotANumber : Problem
        data object OriginalAmountZero : Problem
        data object OriginalAmountTooLarge : Problem
        data object ConversionRateMissing : Problem
        data object ConversionRateNotANumber : Problem
        data object ConversionRateNotPositive : Problem
        data object PayerMissing : Problem
        data object NoParticipantsSelected : Problem
        data class ShareNotANumber(val participantId: String) : Problem
        data class ShareNotPositive(val participantId: String) : Problem

        /** The tax field in a BY_AMOUNT split: blank is fine, a number is fine, nonsense is not. */
        data object TaxNotANumber : Problem
        data object TaxNegative : Problem

        /** Shares must add up to the expense total. [difference] is what's left to allocate. */
        data class AmountsDoNotSumToTotal(val difference: Int) : Problem

        /** Percentages must add up to 100. [difference] is in hundredths of a percent. */
        data class PercentagesDoNotSumTo100(val difference: Int) : Problem

        /** Wording follows the web app's error messages so the two don't drift apart. */
        val message: String
            get() = when (this) {
                TitleTooShort -> "Enter at least two characters."
                AmountMissing -> "You must enter an amount."
                AmountNotANumber -> "Invalid number."
                AmountZero -> "The amount must not be zero."
                AmountTooLarge -> "The amount must be lower than 10,000,000."
                OriginalAmountMissing -> "Enter what was actually paid."
                OriginalAmountNotANumber -> "Invalid number."
                OriginalAmountZero -> "The amount must not be zero."
                OriginalAmountTooLarge -> "The amount must be lower than 10,000,000."
                ConversionRateMissing -> "Enter an exchange rate."
                ConversionRateNotANumber -> "Invalid number."
                ConversionRateNotPositive -> "The rate must be strictly greater than zero."
                PayerMissing -> "You must select a participant."
                NoParticipantsSelected -> "The expense must be paid for at least one participant."
                is ShareNotANumber -> "Invalid number."
                is ShareNotPositive -> "All shares must be higher than 0."
                TaxNotANumber -> "Invalid number."
                TaxNegative -> "The tax must not be negative."
                is AmountsDoNotSumToTotal -> "Sum of amounts must equal the expense amount."
                is PercentagesDoNotSumTo100 -> "Sum of percentages must equal 100."
            }
    }

    val problems: List<Problem>
        get() {
            val problems = mutableListOf<Problem>()

            if (title.trim().length < 2) problems += Problem.TitleTooShort

            if (conversionRequired) {
                // The total is derived from these two, so they are what there is to get wrong —
                // and the total is still worth checking afterwards, since a small enough rate
                // rounds a real payment down to nothing.
                problems += conversionProblems
                amountMinorUnits?.let { amount ->
                    if (amount == 0) problems += Problem.AmountZero
                    if (amount > MAX_AMOUNT) problems += Problem.AmountTooLarge
                }
            } else if (amountText.trim().isEmpty()) {
                problems += Problem.AmountMissing
            } else {
                val amount = amountMinorUnits
                if (amount == null) {
                    problems += Problem.AmountNotANumber
                } else {
                    if (amount == 0) problems += Problem.AmountZero
                    if (amount > MAX_AMOUNT) problems += Problem.AmountTooLarge
                }
            }

            if (paidById == null) problems += Problem.PayerMissing

            val included = includedParticipants
            if (included.isEmpty()) problems += Problem.NoParticipantsSelected

            if (taxApplies && taxText.isNotBlank()) {
                val tax = taxMinorUnits
                if (tax == null) {
                    problems += Problem.TaxNotANumber
                } else if (tax < 0) {
                    problems += Problem.TaxNegative
                }
            }

            if (splitMode != SplitMode.EVENLY) {
                for (participant in included) {
                    val value = shareValue(participant)
                    if (value == null) {
                        problems += Problem.ShareNotANumber(participant.id)
                    } else if (value <= 0) {
                        problems += Problem.ShareNotPositive(participant.id)
                    }
                }
            }

            // Only worth checking the totals once every individual share is a usable number.
            val sharesAreUsable = problems.none {
                it is Problem.ShareNotANumber || it is Problem.ShareNotPositive
            }

            if (sharesAreUsable && included.isNotEmpty()) {
                val difference = unallocated
                if (difference != null && difference != 0) {
                    when (splitMode) {
                        SplitMode.BY_AMOUNT -> if (amountMinorUnits != null) {
                            problems += Problem.AmountsDoNotSumToTotal(difference)
                        }

                        SplitMode.BY_PERCENTAGE ->
                            problems += Problem.PercentagesDoNotSumTo100(difference)

                        SplitMode.EVENLY, SplitMode.BY_SHARES -> Unit
                    }
                }
            }

            return problems
        }

    private val conversionProblems: List<Problem>
        get() {
            val problems = mutableListOf<Problem>()

            if (originalAmountText.trim().isEmpty()) {
                problems += Problem.OriginalAmountMissing
            } else {
                val paid = originalAmountMinorUnits
                if (paid == null) {
                    problems += Problem.OriginalAmountNotANumber
                } else {
                    if (paid == 0) problems += Problem.OriginalAmountZero
                    if (paid > MAX_AMOUNT) problems += Problem.OriginalAmountTooLarge
                }
            }

            if (conversionRateText.trim().isEmpty()) {
                problems += Problem.ConversionRateMissing
            } else {
                val rate = conversionRate
                if (rate == null) {
                    problems += Problem.ConversionRateNotANumber
                } else if (rate.signum() <= 0) {
                    problems += Problem.ConversionRateNotPositive
                }
            }

            return problems
        }

    val isValid: Boolean get() = problems.isEmpty()

    fun problemsForParticipant(id: String): List<Problem> = problems.filter {
        when (it) {
            is Problem.ShareNotANumber -> it.participantId == id
            is Problem.ShareNotPositive -> it.participantId == id
            else -> false
        }
    }

    // MARK: - Submission

    /** The payload to send, or null if the draft isn't valid. */
    val formValues: ExpenseFormValues?
        get() {
            if (!isValid) return null
            val amount = amountMinorUnits ?: return null
            val paidBy = paidById ?: return null

            val paidFor = includedParticipants.mapNotNull { participant ->
                // Includes this participant's part of the tax: the server sees amounts that
                // add up to the total, and nothing that says a tax was involved.
                val shares = payableShareValue(participant) ?: return@mapNotNull null
                ExpenseFormValues.PaidFor(participant = participant.id, shares = shares)
            }
            val trimmedNotes = notes.trim()

            return ExpenseFormValues(
                title = title.trim(),
                expenseDate = CalendarDay.wire(expenseDate),
                amount = amount,
                category = categoryId,
                paidBy = paidBy,
                paidFor = paidFor,
                splitMode = splitMode,
                // Sent because the web app sends it, and read by neither: the procedures ignore
                // it. What remembers a split here is the phone — see `DefaultSplit`.
                saveDefaultSplittingOptions = saveSplitAsDefault,
                isReimbursement = isReimbursement,
                documents = documents,
                notes = trimmedNotes.ifEmpty { null },
                recurrenceRule = recurrenceRule,
                originalAmount = if (conversionRequired) originalAmountMinorUnits else null,
                originalCurrency = if (conversionRequired) isoCode(originalCurrencyCode) else null,
                conversionRate = if (conversionRequired) conversionRate else null,
            )
        }

    companion object {

        const val PERCENT_SCALE = 10_000

        /** 10,000,000 major units, in minor units — the server's own ceiling. */
        const val MAX_AMOUNT = 10_000_000_00

        /**
         * A blank expense for a group: everyone included, split evenly — or divided however this
         * group's expenses were last said to be divided.
         *
         * @param paidBy whoever the user said they are, when they have said. An ID the group no
         *   longer has falls back to the first participant.
         * @param defaultSplit what the group remembers, if anything. One naming somebody who has
         *   since left is ignored rather than trimmed, so a stale default can never quietly leave
         *   a participant out of the expense being written.
         */
        fun creatingIn(
            group: Group,
            paidBy: String? = null,
            defaultSplit: DefaultSplit? = null,
            locale: Locale = Locale.getDefault(),
            today: LocalDate = CalendarDay.today(),
        ): ExpenseFormDraft {
            val payer = group.participants.firstOrNull { it.id == paidBy }
                ?: group.participants.firstOrNull()
            val split = defaultSplit?.takeIf { it.appliesTo(group.participants) }
            val splitMode = split?.splitMode ?: SplitMode.EVENLY
            val digits = MoneyFormatter.minorUnitDigitsFor(group.currencyCode, locale)

            return ExpenseFormDraft(
                expenseDate = today,
                paidById = payer?.id,
                splitMode = splitMode,
                participants = group.participants.map { participant ->
                    val shares = split?.shares?.get(participant.id)
                    ParticipantShare(
                        id = participant.id,
                        name = participant.name,
                        // With nothing remembered — and under BY_AMOUNT, which remembers no
                        // shares — the split covers the group, which is how a new expense starts.
                        isIncluded = split?.shares == null || shares != null,
                        // No currency precision to hand it: the shares that get here are the ×100
                        // kind, and BY_AMOUNT — the one mode whose shares are money — remembers
                        // none.
                        valueText = shares?.let { text(shares = it, splitMode = splitMode, locale = locale) }
                            ?: "1",
                    )
                },
                locale = locale,
                minorUnitDigits = digits,
                groupCurrencyCode = group.currencyCode,
                originalCurrencyCode = group.currencyCode,
            )
        }

        /** An expense loaded for editing. */
        fun editing(
            expense: ExpenseDetails,
            group: Group,
            locale: Locale = Locale.getDefault(),
        ): ExpenseFormDraft {
            val sharesByParticipant = LinkedHashMap<String, Int>()
            expense.paidFor.forEach { row ->
                sharesByParticipant.putIfAbsent(row.participantId, row.shares)
            }
            val digits = MoneyFormatter.minorUnitDigitsFor(group.currencyCode, locale)
            // An expense saved before the group had a code, or one saved in the group's own
            // currency, has no currency of its own — it is in whatever the group counts in.
            val originalCode = expense.originalCurrency ?: group.currencyCode
            val wasConverted = expense.originalCurrency != null
            val originalDigits = MoneyFormatter.minorUnitDigitsFor(originalCode, locale)

            return ExpenseFormDraft(
                title = expense.title,
                expenseDate = CalendarDay.dayOf(expense.expenseDate),
                amountText = text(value = expense.amount, locale = locale, minorUnitDigits = digits),
                categoryId = expense.categoryId,
                paidById = expense.paidById,
                splitMode = expense.splitMode,
                participants = group.participants.map { participant ->
                    val shares = sharesByParticipant[participant.id]
                    ParticipantShare(
                        id = participant.id,
                        name = participant.name,
                        isIncluded = shares != null,
                        valueText = text(
                            shares = shares ?: 100,
                            splitMode = expense.splitMode,
                            locale = locale,
                            minorUnitDigits = digits,
                        ),
                    )
                },
                isReimbursement = expense.isReimbursement,
                notes = expense.notes.orEmpty(),
                locale = locale,
                minorUnitDigits = digits,
                recurrenceRule = expense.recurrenceRule ?: RecurrenceRule.NONE,
                documents = expense.documents,
                groupCurrencyCode = group.currencyCode,
                originalCurrencyCode = originalCode,
                originalAmountText = if (wasConverted) {
                    expense.originalAmount?.let {
                        text(value = it, locale = locale, minorUnitDigits = originalDigits)
                    } ?: ""
                } else {
                    ""
                },
                conversionRateText = if (wasConverted) {
                    expense.conversionRate?.let { text(rate = it.value, locale = locale) } ?: ""
                } else {
                    ""
                },
            )
        }

        /** A reimbursement prefilled from a suggested settlement on the balances screen. */
        fun settling(
            reimbursement: Reimbursement,
            group: Group,
            title: String,
            locale: Locale = Locale.getDefault(),
            today: LocalDate = CalendarDay.today(),
        ): ExpenseFormDraft {
            val digits = MoneyFormatter.minorUnitDigitsFor(group.currencyCode, locale)

            return ExpenseFormDraft(
                title = title,
                expenseDate = today,
                amountText = text(value = reimbursement.amount, locale = locale, minorUnitDigits = digits),
                // "Payment", the category the web app uses for a settlement.
                categoryId = 1,
                paidById = reimbursement.from,
                splitMode = SplitMode.EVENLY,
                participants = group.participants.map { participant ->
                    ParticipantShare(
                        id = participant.id,
                        name = participant.name,
                        isIncluded = participant.id == reimbursement.to,
                    )
                },
                isReimbursement = true,
                locale = locale,
                minorUnitDigits = digits,
                groupCurrencyCode = group.currencyCode,
                originalCurrencyCode = group.currencyCode,
            )
        }

        /** A currency code worth acting on: three letters, upper case, or nothing. */
        fun isoCode(code: String?): String? {
            val trimmed = code?.trim()?.uppercase(Locale.ROOT) ?: return null
            return if (trimmed.length == 3) trimmed else null
        }

        fun text(
            value: Int,
            locale: Locale,
            minorUnitDigits: Int = 2,
        ): String = MoneyFormatter.plain(minorUnitDigits, locale).plainString(value)

        fun text(rate: BigDecimal, locale: Locale): String =
            MoneyFormatter.text(rate = rate, locale = locale)

        /** Turns a stored `shares` value back into something to show in the field it came from. */
        fun text(
            shares: Int,
            splitMode: SplitMode,
            locale: Locale,
            minorUnitDigits: Int = 2,
        ): String = when (splitMode) {
            // Already a minor-unit amount, in the group's own currency.
            SplitMode.BY_AMOUNT -> text(value = shares, locale = locale, minorUnitDigits = minorUnitDigits)
            // Scaled by 100 whatever the currency; show whole numbers without a pointless ".00".
            SplitMode.EVENLY, SplitMode.BY_SHARES, SplitMode.BY_PERCENTAGE ->
                if (shares % 100 == 0) (shares / 100).toString()
                else text(value = shares, locale = locale)
        }
    }
}
