package app.spliit.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

    /** Drops anything no longer on screen, so an action cannot apply to a row nobody can see. */
    fun retain(present: Set<String>) {
        if (ids.any { it !in present }) ids = ids intersect present
    }
}

/**
 * The checkbox a card grows when a selection is open. Display only: the card itself is the
 * target, so a tap anywhere on it picks or unpicks it, and there is no second thing to hit.
 */
@Composable
fun SelectionCheckbox(selected: Boolean) {
    Checkbox(checked = selected, onCheckedChange = null)
}

/**
 * The checkbox sliding in beside a card's own content, and back out when the selection ends.
 *
 * It grows from nothing rather than appearing: the card's leading edge is where the finger is
 * when the long press happens, and a control that pops in under it reads as a mis-tap.
 */
@Composable
fun SelectionCheckboxSlot(visible: Boolean, selected: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(SpliitMotion.spatialFast()) + fadeIn(SpliitMotion.effectsDefault()),
        exit = shrinkHorizontally(SpliitMotion.spatialFast()) + fadeOut(SpliitMotion.effectsFast()),
    ) {
        SelectionCheckbox(selected)
    }
}
