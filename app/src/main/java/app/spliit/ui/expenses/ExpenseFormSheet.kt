package app.spliit.ui.expenses

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.spliit.AppContainer
import app.spliit.data.local.DefaultSplit
import app.spliit.data.local.PendingExpense
import app.spliit.data.model.ExpenseCategory
import app.spliit.data.model.ExpenseDetails
import app.spliit.data.model.Group
import app.spliit.data.model.SplitMode
import app.spliit.data.remote.SpliitApi
import app.spliit.data.remote.TrpcServerException
import app.spliit.domain.CalendarDay
import app.spliit.domain.ExpenseFormDraft
import app.spliit.domain.ParticipantShare
import app.spliit.ui.theme.ExpandChevron
import app.spliit.ui.theme.ExpressiveMenu
import app.spliit.ui.theme.ExpressiveMenuItem
import app.spliit.domain.ReceiptPhotos
import app.spliit.ui.theme.Money
import app.spliit.ui.theme.MoneySize
import app.spliit.ui.theme.SpliitDimens
import app.spliit.ui.theme.SpliitType
import app.spliit.ui.theme.stackedCardShape
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Creating and editing an expense — who paid, how much, and how it's divided.
 *
 * The split picker is four segments over one list of participants, as the reference draws it;
 * what each share comes to is printed beside the name, from the same apportionment the balances
 * tab uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormSheet(
    container: AppContainer,
    group: Group,
    /** The expense being edited, or null for a new one. */
    editing: ExpenseDetails?,
    initialDraft: ExpenseFormDraft,
    /** The ID a new expense is created under: it decides who is offered the odd cent. */
    mintedExpenseId: String,
    /** What the category picker offers. Empty while the instance has not answered. */
    categories: List<ExpenseCategory>,
    /** Whether this group's instance stores receipt photographs: off, the section is not shown. */
    receiptsEnabled: Boolean = true,
    /** The instance this group is on: an expense is written to the server the group is on. */
    instanceUrl: String,
    actorId: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var draft by remember(initialDraft, editing?.id) { mutableStateOf(initialDraft) }
    var hasAttemptedSave by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var payerMenuOpen by remember { mutableStateOf(false) }
    var uploadingReceipt by remember { mutableStateOf(false) }
    var receiptFailure by remember { mutableStateOf<String?>(null) }

    val client = remember(instanceUrl) { container.client(instanceUrl) }
    val expenseId = editing?.id ?: mintedExpenseId
    val shares = if (draft.showsShareAmounts) draft.shareAmounts(expenseId) else null
    val context = LocalContext.current
    // The photograph the camera is writing to, between the tap and its result.
    var cameraTarget by remember { mutableStateOf<Uri?>(null) }

    /**
     * Reads the photograph and sends it, trying each of [candidates] in turn.
     *
     * A camera is asked to write the picture into a file this app owns, but it may hand back a
     * URI of its own as well, and that one is not always readable here — the grant belongs to the
     * camera, not to this form. So both are tried: the one the camera returned, then the file it
     * was told to write into.
     */
    fun upload(candidates: List<Uri>) {
        uploadingReceipt = true
        receiptFailure = null
        scope.launch {
            try {
                val photo = withContext(Dispatchers.IO) {
                    var refused: Exception? = null
                    val found = candidates.firstNotNullOfOrNull { candidate ->
                        try {
                            ReceiptPhotos.read(context, candidate)
                        } catch (error: Exception) {
                            // Kept rather than dropped: a photo that cannot be read is worth a
                            // reason, and "couldn't be opened" is not one when it could.
                            refused = error
                            null
                        }
                    }
                    found ?: throw refused ?: IllegalArgumentException("That photo couldn’t be opened.")
                }
                val document = client.uploadDocument(
                    bytes = photo.bytes,
                    mimeType = photo.mimeType,
                    fileName = photo.fileName,
                    width = photo.width,
                    height = photo.height,
                )
                draft = draft.copy(documents = draft.documents + document)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                receiptFailure = error.localizedMessage ?: "Couldn’t add the receipt."
            } finally {
                uploadingReceipt = false
            }
        }
    }

    // A receipt is uploaded the moment it is picked, not when the expense is saved: the photo is
    // somebody's evidence that the expense happened, and losing the form must not lose it.
    //
    // The picker is the system's own: one chooser offering the camera and the file app, so the
    // photo comes from wherever it actually is. The camera writes into a file this app owns and
    // hands back through the FileProvider, which is why a picture taken here reads back as
    // reliably as one chosen from storage.
    val receiptPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val chosen = listOfNotNull(result.data?.data, cameraTarget).distinct()
        if (chosen.isNotEmpty()) upload(chosen)
    }

    fun pickReceipt() {
        val target = runCatching {
            val directory = File(context.cacheDir, "receipts").apply { mkdirs() }
            val file = File.createTempFile("receipt-", ".jpg", directory)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
        if (target == null) {
            // No provider to hand a camera: the file app still works, and says so if it does not.
            receiptFailure = "This phone can’t take a photo here. Choose a file instead."
            return
        }
        cameraTarget = target
        val capture = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, target)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val files = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        receiptPicker.launch(
            Intent.createChooser(files, "Add a receipt").apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(capture))
            },
        )
    }

    /** Whether a failure means nothing was sent, rather than the server refusing what was sent. */
    fun offline(error: Exception): Boolean =
        error is IOException && error !is TrpcServerException

    /**
     * Keeps a change that could not be sent, so closing the app does not lose it.
     *
     * The request is stored exactly as it was built, which is what lets it be sent later without
     * the form existing any more.
     */
    fun queue(values: app.spliit.data.remote.ExpenseFormValues) {
        val category = categories.firstOrNull { it.id == values.category }
        container.outbox.put(
            PendingExpense(
                key = expenseId,
                groupId = group.id,
                kind = if (editing == null) {
                    PendingExpense.Kind.CREATE
                } else {
                    PendingExpense.Kind.UPDATE
                },
                valuesJson = values.toJson().toString(),
                actorId = actorId,
                title = values.title,
                amount = values.amount,
                expenseDateEpochMillis = draft.expenseDate
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli(),
                categoryId = values.category,
                categoryName = category?.name,
                categoryGrouping = category?.grouping,
                paidById = values.paidBy,
                paidFor = values.paidFor.map {
                    PendingExpense.PaidFor(it.participant, it.shares)
                },
                isReimbursement = values.isReimbursement,
                splitMode = values.splitMode,
                queuedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun save() {
        hasAttemptedSave = true
        val values = draft.formValues ?: return
        isSaving = true
        scope.launch {
            try {
                if (editing == null) {
                    client.call(
                        SpliitApi.createExpense(
                            groupId = group.id,
                            values = values,
                            byParticipantId = actorId,
                            expenseId = mintedExpenseId,
                        ),
                    )
                } else {
                    client.call(
                        SpliitApi.updateExpense(
                            groupId = group.id,
                            expenseId = editing.id,
                            values = values,
                            byParticipantId = actorId,
                        ),
                    )
                }
                // Only once the expense is actually written: a split remembered from a save the
                // server refused would go on prefilling expenses that never happened.
                if (draft.saveSplitAsDefault && draft.isSplitWorthRemembering) {
                    container.recentGroups.setDefaultSplit(
                        DefaultSplit.remembering(values, group.participants),
                        group.id,
                    )
                }
                onSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (offline(error)) {
                    // Saved, from the person's side: it is on the list, marked as waiting, and it
                    // survives closing the app.
                    queue(values)
                    onSaved()
                } else {
                    failure = error.localizedMessage ?: "Couldn’t save the expense."
                }
            } finally {
                isSaving = false
            }
        }
    }

    // A form is not a peek: dragging it is how a long one gets closed by accident while the
    // thumb is scrolling its fields. The gestures are off and the handle is gone, so the only
    // ways out are the Cancel button, the scrim and back - all of them deliberate.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Text(if (editing == null) "New expense" else "Edit expense", style = SpliitType.RowTitle)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { save() }, enabled = !isSaving) {
                    Text(if (isSaving) "Saving…" else "Save")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = { draft = draft.copy(title = it) },
                        label = { Text("What was it for?") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                problemText(draft, hasAttemptedSave, ExpenseFormDraft.Problem.TitleTooShort)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Amount", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    if (draft.conversionRequired) {
                        val total = draft.amountMinorUnits
                        if (total == null) {
                            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Money(value = groupFormatter(group).string(total), size = MoneySize.ROW)
                        }
                    } else {
                        OutlinedTextField(
                            value = draft.amountText,
                            onValueChange = { draft = draft.copy(amountText = it) },
                            placeholder = { Text("0.00") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(160.dp),
                        )
                    }
                }
                problemText(
                    draft,
                    hasAttemptedSave,
                    ExpenseFormDraft.Problem.AmountMissing,
                    ExpenseFormDraft.Problem.AmountNotANumber,
                    ExpenseFormDraft.Problem.AmountZero,
                    ExpenseFormDraft.Problem.AmountTooLarge,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerOpen = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Date", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Text(dateLabel(draft.expenseDate))
                }

                // The payer: one row, one menu. A group is a handful of people.
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { payerMenuOpen = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Paid by", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = group.participants.firstOrNull { it.id == draft.paidById }?.name
                                ?: "Choose",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ExpandChevron(
                                expanded = payerMenuOpen,
                            )
                    }
                    ExpressiveMenu(
                        expanded = payerMenuOpen,
                        onDismissRequest = { payerMenuOpen = false },
                    ) {
                        group.participants.forEach { participant ->
                            ExpressiveMenuItem(
                                text = participant.name,
                                selected = participant.id == draft.paidById,
                                onClick = {
                                    payerMenuOpen = false
                                    draft = draft.copy(paidById = participant.id)
                                },
                            )
                        }
                    }
                }
                problemText(draft, hasAttemptedSave, ExpenseFormDraft.Problem.PayerMissing)

                CategoryPicker(
                    categories = categories,
                    selectedId = draft.categoryId,
                    onSelect = { draft = draft.copy(categoryId = it) },
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("This is a reimbursement", modifier = Modifier.weight(1f))
                    Switch(
                        checked = draft.isReimbursement,
                        onCheckedChange = { draft = draft.copy(isReimbursement = it) },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                // The split, as the reference presents it: the mode, then who is in it, then
                // what each of them owes.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Paid for", style = SpliitType.SectionHeader)
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            draft = draft.setAllParticipantsIncluded(!draft.allParticipantsIncluded)
                        },
                    ) {
                        Text(if (draft.allParticipantsIncluded) "Select none" else "Select all")
                    }
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SplitMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = draft.splitMode == mode,
                            onClick = { draft = draft.copy(splitMode = mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, SplitMode.entries.size),
                        ) {
                            Text(mode.label)
                        }
                    }
                }

                // The people being split between are one stack of cards, the same as a day's
                // expenses in the list: shared sides take the small corner, the ends take the
                // card's own, and the gap between them is the stack gap. A plain run of rows
                // read as a form; this reads as the group it is.
                draft.participants.forEachIndexed { index, participant ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = if (index == 0) {
                                    SpliitDimens.Space0
                                } else {
                                    SpliitDimens.CardGap
                                },
                            ),
                        shape = stackedCardShape(
                            first = index == 0,
                            last = index == draft.participants.lastIndex,
                        ),
                        // The sheet itself is already `surfaceContainerLow`, so a card in that
                        // role would be white on white. One step up the tonal ladder is what
                        // makes the stack visible inside a sheet.
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = SpliitDimens.RowPaddingHorizontal,
                                vertical = SpliitDimens.RowPaddingVertical,
                            ),
                        ) {
                            ParticipantRow(
                                participant = participant,
                                splitMode = draft.splitMode,
                                shareAmount = shares?.get(participant.id),
                                moneyFormatter = groupFormatter(group),
                                onChangeIncluded = { included ->
                                    draft = draft.copy(
                                        participants = draft.participants.map {
                                            if (it.id == participant.id) {
                                                it.copy(isIncluded = included)
                                            } else {
                                                it
                                            }
                                        },
                                    ).rebalanced()
                                },
                                onChangeValue = { value ->
                                    draft = draft.copy(
                                        participants = draft.participants.map {
                                            if (it.id == participant.id) {
                                                it.copy(valueText = value)
                                            } else {
                                                it
                                            }
                                        },
                                    ).rebalanced()
                                },
                            )
                            if (hasAttemptedSave) {
                                draft.problemsForParticipant(participant.id).firstOrNull()?.let {
                                    FieldError(it.message)
                                }
                            }
                        }
                    }
                }

                // Splitting by amount is the one mode where a total is typed by hand and then
                // divided up, which is exactly the shape of a bill with tax on it. The tax is
                // added to the total and shared equally, so what is sent is still one BY_AMOUNT
                // expense whose shares add up to its amount.
                if (draft.splitMode == SplitMode.BY_AMOUNT) {
                    OutlinedTextField(
                        value = draft.taxText,
                        onValueChange = { draft = draft.copy(taxText = it).rebalanced() },
                        label = { Text("Tax, split equally") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        suffix = { Text(groupFormatter(group).currencySymbol) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    problemText(
                        draft,
                        hasAttemptedSave,
                        ExpenseFormDraft.Problem.TaxNotANumber,
                        ExpenseFormDraft.Problem.TaxNegative,
                    )
                }

                if (draft.isSplitWorthRemembering) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Save as default split", modifier = Modifier.weight(1f))
                        Switch(
                            checked = draft.saveSplitAsDefault,
                            onCheckedChange = { draft = draft.copy(saveSplitAsDefault = it) },
                        )
                    }
                }

                SplitFooter(draft, hasAttemptedSave, group)

                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { draft = draft.copy(notes = it) },
                    label = { Text("Notes") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Documents are opt-in on a Spliit instance. Where the server has no bucket,
                // offering the button would be offering an upload that can only be refused, so
                // the row is not there at all.
                if (receiptsEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                    ReceiptAttachments(
                        documents = draft.documents,
                        uploading = uploadingReceipt,
                        failure = receiptFailure,
                        onAdd = { if (!uploadingReceipt) pickReceipt() },
                        onRemove = { document ->
                            draft = draft.copy(
                                documents = draft.documents.filterNot { it.id == document.id },
                            )
                        },
                    )
                }

                if (editing != null) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                try {
                                    client.call(
                                        SpliitApi.deleteExpense(group.id, editing.id, actorId),
                                    )
                                    onSaved()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    failure = error.localizedMessage
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete expense", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (datePickerOpen) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = draft.expenseDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            draft = draft.copy(
                                expenseDate = java.time.Instant.ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate(),
                            )
                        }
                        datePickerOpen = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    failure?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { failure = null },
            title = { Text("Couldn’t save the expense") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { failure = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun ParticipantRow(
    participant: ParticipantShare,
    splitMode: SplitMode,
    shareAmount: Int?,
    moneyFormatter: app.spliit.domain.MoneyFormatter,
    onChangeIncluded: (Boolean) -> Unit,
    onChangeValue: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = participant.isIncluded, onCheckedChange = onChangeIncluded)
            Text(participant.name, modifier = Modifier.weight(1f))

            if (participant.isIncluded && splitMode != SplitMode.EVENLY) {
                OutlinedTextField(
                    value = participant.valueText,
                    onValueChange = onChangeValue,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(100.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = splitMode.unitLabel(moneyFormatter.currencySymbol),
                    style = SpliitType.Secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (participant.isIncluded && shareAmount != null) {
            Money(
                value = moneyFormatter.string(shareAmount),
                size = MoneySize.SUPPORT,
                modifier = Modifier.padding(start = 48.dp),
            )
        }
    }
}

@Composable
private fun SplitFooter(draft: ExpenseFormDraft, hasAttemptedSave: Boolean, group: Group) {
    val problem = if (hasAttemptedSave) {
        draft.problems.firstOrNull {
            it is ExpenseFormDraft.Problem.AmountsDoNotSumToTotal ||
                it is ExpenseFormDraft.Problem.PercentagesDoNotSumTo100 ||
                it is ExpenseFormDraft.Problem.NoParticipantsSelected ||
                it is ExpenseFormDraft.Problem.ShareNotPositive ||
                it is ExpenseFormDraft.Problem.ShareNotANumber
        }
    } else {
        null
    }

    val remainder = draft.unallocated
    when {
        problem != null -> FieldError(problem.message)

        remainder != null && remainder != 0 -> {
            val text = when (draft.splitMode) {
                SplitMode.BY_AMOUNT -> {
                    val amount = groupFormatter(group).string(kotlin.math.abs(remainder))
                    if (remainder > 0) "$amount still to allocate." else "$amount over the total."
                }

                SplitMode.BY_PERCENTAGE -> {
                    val percent = kotlin.math.abs(remainder) / 100.0
                    val label = if (percent % 1.0 == 0.0) percent.toInt().toString() else percent.toString()
                    if (remainder > 0) "$label% still to allocate." else "$label% over 100%."
                }

                else -> ""
            }
            Text(
                text = text,
                style = SpliitType.Secondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> Text(
            text = draft.splitMode.explanation,
            style = SpliitType.Secondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun problemText(
    draft: ExpenseFormDraft,
    hasAttemptedSave: Boolean,
    vararg candidates: ExpenseFormDraft.Problem,
) {
    if (!hasAttemptedSave) return
    val problem = draft.problems.firstOrNull { it in candidates } ?: return
    FieldError(problem.message)
}

@Composable
private fun FieldError(text: String) {
    Text(text = text, style = SpliitType.Secondary, color = MaterialTheme.colorScheme.error)
}

private val SplitMode.label: String
    get() = when (this) {
        SplitMode.EVENLY -> "Evenly"
        SplitMode.BY_SHARES -> "Shares"
        SplitMode.BY_PERCENTAGE -> "Percent"
        SplitMode.BY_AMOUNT -> "Amount"
    }

private val SplitMode.explanation: String
    get() = when (this) {
        SplitMode.EVENLY -> "Everyone selected pays an equal part."
        SplitMode.BY_SHARES -> "Give anyone paying a larger part more shares."
        SplitMode.BY_PERCENTAGE -> "Percentages must add up to 100."
        SplitMode.BY_AMOUNT -> "Amounts must add up to the expense total."
    }

private fun SplitMode.unitLabel(currency: String): String = when (this) {
    SplitMode.EVENLY -> ""
    SplitMode.BY_SHARES -> "shares"
    SplitMode.BY_PERCENTAGE -> "%"
    SplitMode.BY_AMOUNT -> currency
}

private fun groupFormatter(group: Group): app.spliit.domain.MoneyFormatter =
    app.spliit.domain.MoneyFormatter(group.currency, group.currencyCode)

private fun dateLabel(date: LocalDate): String =
    java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
        .format(date)
