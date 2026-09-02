package com.example.linkup.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The four shapes a friend control can take.
 *
 * A `core`-local enum rather than the domain one: the design system stays free of
 * `data` types, and callers map their own status across explicitly.
 */
enum class FriendActionState {
    /** No relationship — offer to add. */
    ADD,

    /** You asked and are waiting; tapping withdraws. */
    REQUESTED,

    /** They asked; you confirm or delete. */
    RESPOND,

    /** Already friends; the menu offers to unfriend. */
    FRIENDS
}

/**
 * Friend controls for a list row or a profile header.
 *
 * [RESPOND] is deliberately two buttons rather than one menu: answering a request is
 * the single most common action in this screen, and burying "Confirm" behind a tap
 * would make the Requests tab slower than every competitor's.
 */
@Composable
fun FriendControls(
    state: FriendActionState,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onUnfriend: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    compact: Boolean = true
) {
    when (state) {
        FriendActionState.ADD -> ActionPill(
            text = if (compact) "Add" else "Add friend",
            onClick = onAdd,
            modifier = modifier,
            filled = true,
            isBusy = isBusy
        )

        FriendActionState.REQUESTED -> ActionPill(
            text = "Requested",
            onClick = onCancel,
            modifier = modifier,
            filled = false,
            isBusy = isBusy
        )

        FriendActionState.RESPOND -> Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionPill(text = "Confirm", onClick = onAccept, filled = true, isBusy = isBusy)
            ActionPill(text = "Delete", onClick = onDecline, filled = false, danger = true, isBusy = isBusy)
        }

        FriendActionState.FRIENDS -> {
            var menuOpen by remember { mutableStateOf(false) }
            Box(modifier) {
                ActionPill(
                    text = "Friends ▾",
                    onClick = { menuOpen = true },
                    filled = false,
                    isBusy = isBusy
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Unfriend", color = Color(0xFFB3261E)) },
                        onClick = { menuOpen = false; onUnfriend() }
                    )
                }
            }
        }
    }
}
