package com.example.linkup.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Reports the user's vertical scroll direction after a small threshold, preventing
 * tiny finger movements from repeatedly showing and hiding the primary navigation.
 */
@Composable
fun rememberNavigationVisibilityScrollConnection(
    onVisibilityChanged: (Boolean) -> Unit,
): NestedScrollConnection {
    val currentCallback = rememberUpdatedState(onVisibilityChanged)
    val thresholdPx = with(LocalDensity.current) { 20.dp.toPx() }

    return remember(thresholdPx) {
        object : NestedScrollConnection {
            private var accumulatedDelta = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero

                if (accumulatedDelta != 0f && accumulatedDelta * available.y < 0f) {
                    accumulatedDelta = 0f
                }
                accumulatedDelta += available.y

                if (abs(accumulatedDelta) >= thresholdPx) {
                    currentCallback.value(accumulatedDelta > 0f)
                    accumulatedDelta = 0f
                }
                return Offset.Zero
            }
        }
    }
}
