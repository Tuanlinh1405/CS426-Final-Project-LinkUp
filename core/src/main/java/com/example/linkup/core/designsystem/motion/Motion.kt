package com.example.linkup.core.designsystem.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Shared motion vocabulary.
 *
 * Durations live here rather than at call sites so the whole app moves at one speed.
 * The values follow Material's guidance: short feedback is quick enough to feel
 * instant, screen-level movement is slow enough to be followed by the eye.
 */
object Motion {
    /** Colour and small state changes. */
    const val QUICK_MS = 180

    /** Cross-fades between screen phases such as loading to content. */
    const val MEDIUM_MS = 260

    /** One full sweep of a loading shimmer. */
    const val SHIMMER_MS = 1250
}

/**
 * A gradient that sweeps across placeholder blocks while content loads.
 *
 * A static grey rectangle reads as broken layout; a moving highlight reads as work in
 * progress. Returned as a [Brush] rather than a Modifier so callers keep control of
 * their own shape and clipping.
 */
@Composable
fun rememberShimmerBrush(
    base: Color = Color(0xFFEDE9F5),
    highlight: Color = Color(0xFFF8F6FC)
): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.SHIMMER_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    // The band travels from well before the shape to well past it, so the highlight
    // enters and leaves cleanly instead of appearing to bounce.
    val width = 320f
    val start = progress * (width * 3) - width
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(start, 0f),
        end = Offset(start + width, 0f)
    )
}

/**
 * Shrinks a control slightly while it is held.
 *
 * Ripple alone tells you *where* you touched; a scale tells you the control accepted
 * the press. Spring-based so a quick tap still settles naturally rather than snapping.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.95f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
