package app.spliit.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The rows one screen has picked out.
 *
 * A long press on a card starts a selection, and while one is open the screen stops being a list
 * to open and becomes a set of things to act on: a tap picks a card instead of opening it, a
 * checkbox appears beside it, and the header turns into the actions that apply to what is picked.
 * That is the arrangement Google Drive uses, and it is the one people already know.
 *
 * An empty selection is not a mode: [active] is false and every screen draws its ordinary header.
 * The last card being unpicked ends the selection, so there is no state to leave behind.
 */
@Stable
class SelectionState {

    var ids by mutableStateOf<Set<String>>(emptySet())
        private set

    val active: Boolean get() = ids.isNotEmpty()

    val count: Int get() = ids.size

    operator fun contains(id: String): Boolean = id in ids

    /** Starts a selection with [id] in it, for the row that was long-pressed. */
    fun start(id: String) {
        ids = setOf(id)
    }

    fun toggle(id: String) {
        ids = if (id in ids) ids - id else ids + id
    }

    fun clear() {
        ids = emptySet()
    }

    /** Picks everything [present] names — the rows a "select all" can see. */
    fun selectAll(present: Collection<String>) {
        ids = present.toSet()
    }

    /** Whether [present] is already picked in full, so the same button can unpick it. */
    fun containsAll(present: Collection<String>): Boolean =
        present.isNotEmpty() && ids.containsAll(present)

    /** Drops anything no longer on screen, so an action cannot apply to a row nobody can see. */
    fun retain(present: Set<String>) {
        if (ids.any { it !in present }) ids = ids intersect present
    }
}

/**
 * The mark a card grows when a selection is open: a circle rather than a square checkbox, so it
 * sits where the chevron was and reads as a pick rather than as a form field.
 *
 * Display only. The card itself is the target, so a tap anywhere on it picks or unpicks it and
 * there is no second thing to hit.
 */
@Composable
fun SelectionCircle(selected: Boolean, modifier: Modifier = Modifier) {
    val fill by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = SpliitMotion.effectsDefault(),
        label = "selection circle fill",
    )
    val edge by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        animationSpec = SpliitMotion.effectsDefault(),
        label = "selection circle edge",
    )

    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, edge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * The circle sliding in at a card's trailing edge, and back out when the selection ends.
 *
 * It grows from nothing rather than appearing: on the right it lines up with the chevron the
 * card shows when it is not selecting, which is where the eye already goes for "there is more
 * here" — now it says what the tap would do.
 */
@Composable
fun SelectionCircleSlot(visible: Boolean, selected: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(SpliitMotion.spatialFast()) + fadeIn(SpliitMotion.effectsDefault()),
        exit = shrinkHorizontally(SpliitMotion.spatialFast()) + fadeOut(SpliitMotion.effectsFast()),
    ) {
        SelectionCircle(selected)
    }
}
