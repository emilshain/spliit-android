package app.spliit.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.spliit.data.local.ActiveParticipant
import app.spliit.data.model.Group
import app.spliit.data.model.Participant
import app.spliit.ui.theme.Monogram
import app.spliit.ui.theme.MonogramPalette
import app.spliit.ui.theme.SpliitType

/**
 * "Who are you in this group?" — asked once per group, answered locally.
 *
 * The web app asks this in a modal the first time a group is opened. This one is offered beside
 * the answer it unlocks (the group's information), not as a toll gate on the way to expenses.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ActiveUserPickerSheet(
    group: Group,
    selection: ActiveParticipant?,
    onSelect: (ActiveParticipant) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Who are you?",
                style = SpliitType.RowTitle,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            group.participants.forEachIndexed { position, participant ->
                ParticipantRow(
                    participant = participant,
                    position = position,
                    isSelected = selection?.participantId == participant.id,
                    onClick = { onSelect(ActiveParticipant.Participant(participant.id)) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(ActiveParticipant.Nobody) }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Nobody",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (selection == ActiveParticipant.Nobody) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: Participant,
    position: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Monogram(
            name = participant.name,
            colorIndex = MonogramPalette.indexAtPosition(position),
            size = 28.dp,
        )
        Text(text = participant.name)
        Spacer(Modifier.weight(1f))
        if (isSelected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
