package app.spliit.domain

import app.spliit.data.model.Group
import app.spliit.data.remote.GroupFormValues
import java.util.Locale
import java.util.UUID

/**
 * A participant being edited.
 *
 * [id] is local identity, for Compose's sake; [serverId] is set only for participants that
 * already exist on the server.
 */
data class ParticipantDraft(
    val id: String = UUID.randomUUID().toString(),
    val serverId: String? = null,
    val name: String = "",
)

/**
 * The group form's state, plus the validation the server would apply.
 *
 * Validation mirrors `groupFormSchema` in the web app. Checking here means the user sees the
 * problem next to the field instead of as a failed request, but the server stays the authority —
 * a rejected mutation is still surfaced.
 */
data class GroupFormDraft(
    val name: String = "",
    /** A free-text symbol like "$" or "CHF"; this is what amounts are displayed with. */
    val currency: String = "$",
    /**
     * ISO-4217, when the group has one. Picking a currency sets this and [currency] together;
     * null is what "custom symbol" means, and it is how groups made before codes existed stay
     * legal. The web app writes an empty string for the same state.
     */
    val currencyCode: String? = null,
    val information: String = "",
    val participants: List<ParticipantDraft> = listOf(
        ParticipantDraft(name = "John"),
        ParticipantDraft(name = "Jane"),
        ParticipantDraft(name = "Jack"),
    ),
) {

    // MARK: - Currency

    /**
     * True when the group carries only a symbol: either it predates ISO codes, or the user chose
     * to type their own. The symbol is theirs to edit in that state and nobody else's.
     */
    val usesCustomSymbol: Boolean
        get() = currencyCode.isNullOrBlank()

    /** The currency the group is set to, or null when it has none the platform recognises. */
    fun selectedCurrency(locale: Locale = Locale.getDefault()): CurrencyInfo? =
        currencyCode?.let { CurrencyCatalog.named(it, locale) }

    /**
     * Picking a currency sets the symbol too — the code is what the amounts are in, and a code
     * that disagreed with the symbol beside every amount would be worse than no code at all.
     */
    fun use(currency: CurrencyInfo): GroupFormDraft =
        copy(currencyCode = currency.code, currency = currency.symbol)

    /** Drops the ISO code and keeps the symbol, which is now the user's to edit. */
    fun useCustomSymbol(): GroupFormDraft = copy(currencyCode = null)

    // MARK: - Validation

    sealed interface Problem {
        data object NameTooShort : Problem
        data object NameTooLong : Problem
        data object CurrencyMissing : Problem
        data object CurrencyTooLong : Problem
        data object CurrencyCodeInvalid : Problem
        data object NoParticipants : Problem
        data class ParticipantNameTooShort(val participantId: String) : Problem
        data class ParticipantNameTooLong(val participantId: String) : Problem
        data class DuplicateParticipantName(val participantId: String) : Problem

        /** Wording follows the web app's messages so the two don't drift apart. */
        val message: String
            get() = when (this) {
                NameTooShort -> "Enter at least two characters."
                NameTooLong -> "Enter at most 50 characters."
                CurrencyMissing -> "Enter at least one character."
                CurrencyTooLong -> "Enter at most five characters."
                CurrencyCodeInvalid ->
                    "This group’s currency code isn’t valid. Pick a currency, or choose a custom symbol."
                NoParticipants -> "A group needs at least one participant."
                is ParticipantNameTooShort -> "Enter at least two characters."
                is ParticipantNameTooLong -> "Enter at most 50 characters."
                is DuplicateParticipantName -> "Another participant already has this name."
            }
    }

    val problems: List<Problem>
        get() {
            val found = mutableListOf<Problem>()

            val trimmedName = name.trim()
            if (trimmedName.length < 2) found += Problem.NameTooShort
            if (trimmedName.length > 50) found += Problem.NameTooLong

            val trimmedCurrency = currency.trim()
            if (trimmedCurrency.isEmpty()) found += Problem.CurrencyMissing
            if (trimmedCurrency.length > 5) found += Problem.CurrencyTooLong

            // The picker cannot produce anything else, but an instance could have stored one —
            // and the server would reject the save with a message about a field the form
            // doesn't show.
            if (!usesCustomSymbol && currencyCode?.length != 3) found += Problem.CurrencyCodeInvalid

            if (participants.isEmpty()) found += Problem.NoParticipants

            // The server reports a duplicate against the *later* of the two, so the first
            // occurrence stays unmarked. Matching that keeps the errors where a user expects.
            val seen = mutableSetOf<String>()
            for (participant in participants) {
                val trimmed = participant.name.trim()
                if (trimmed.length < 2) found += Problem.ParticipantNameTooShort(participant.id)
                if (trimmed.length > 50) found += Problem.ParticipantNameTooLong(participant.id)
                if (trimmed.isNotEmpty() && trimmed in seen) {
                    found += Problem.DuplicateParticipantName(participant.id)
                }
                seen += trimmed
            }

            return found
        }

    val isValid: Boolean get() = problems.isEmpty()

    fun problemsForParticipant(id: String): List<Problem> = problems.filter {
        when (it) {
            is Problem.ParticipantNameTooShort -> it.participantId == id
            is Problem.ParticipantNameTooLong -> it.participantId == id
            is Problem.DuplicateParticipantName -> it.participantId == id
            else -> false
        }
    }

    // MARK: - Submission

    /** The payload to send. Only meaningful once [isValid] is true. */
    val formValues: GroupFormValues
        get() {
            val trimmedInformation = information.trim()
            return GroupFormValues(
                name = name.trim(),
                information = trimmedInformation.ifEmpty { null },
                currency = currency.trim(),
                // Empty rather than null, and this is load-bearing: a null optional is left out
                // of the JSON entirely, which reaches the server as `undefined` and tells Prisma
                // to leave the column alone. A group being moved off its ISO code would keep
                // it. The web app writes an empty string here for the same state.
                currencyCode = if (usesCustomSymbol) "" else currencyCode,
                participants = participants.map {
                    GroupFormValues.ParticipantInput(
                        id = it.serverId,
                        name = it.name.trim(),
                    )
                },
            )
        }

    companion object {

        /**
         * A new group, denominated in the currency the device is set to — the answer for most
         * groups, and one the picker is there to change for the rest. Falls back to the dollar,
         * which is what the web app defaults to when nothing else says otherwise.
         */
        fun newGroup(locale: Locale = Locale.getDefault()): GroupFormDraft {
            val currency = CurrencyCatalog.device(locale)
            return GroupFormDraft(
                currency = currency?.symbol ?: "$",
                currencyCode = currency?.code,
            )
        }

        /** Builds a draft for editing an existing group. */
        fun editing(group: Group): GroupFormDraft = GroupFormDraft(
            name = group.name,
            currency = group.currency,
            currencyCode = group.currencyCode,
            information = group.information.orEmpty(),
            participants = group.participants.map {
                ParticipantDraft(serverId = it.id, name = it.name)
            },
        )
    }
}
