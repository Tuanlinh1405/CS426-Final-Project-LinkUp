package com.example.linkup.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * LinkUp's icon set, drawn as vectors rather than pulled from a library.
 *
 * `material-icons` is not on this project's classpath — Material 3 no longer brings
 * it transitively, and the standalone artifact is frozen upstream. Hand-built
 * [ImageVector]s add no dependency, cannot drift out of step with the Compose BOM,
 * and let every glyph share one weight and corner treatment.
 *
 * All icons use a 24×24 viewport with a 2dp round-capped stroke, so they sit evenly
 * next to each other at any size. Tint comes from `Icon(tint = …)` at the call site.
 */
object LinkUpIcons {

    // ---- bottom navigation ------------------------------------------------

    val Home: ImageVector = stroked("Home") {
        moveTo(3f, 10.2f); lineTo(12f, 3f); lineTo(21f, 10.2f)
        lineTo(21f, 20f); lineTo(3f, 20f); close()
        moveTo(9.2f, 20f); lineTo(9.2f, 13.8f); lineTo(14.8f, 13.8f); lineTo(14.8f, 20f)
    }

    val HomeFilled: ImageVector = filled("HomeFilled") {
        moveTo(3f, 10.2f); lineTo(12f, 3f); lineTo(21f, 10.2f)
        lineTo(21f, 20f); lineTo(14.8f, 20f); lineTo(14.8f, 14f); lineTo(9.2f, 14f)
        lineTo(9.2f, 20f); lineTo(3f, 20f); close()
    }

    val Reels: ImageVector = stroked("Reels") {
        roundedRect(3.5f, 4f, 20.5f, 20f, 3f)
        moveTo(3.5f, 9f); lineTo(20.5f, 9f)
        moveTo(8.5f, 4f); lineTo(11f, 9f)
        moveTo(14f, 4f); lineTo(16.5f, 9f)
        moveTo(10.5f, 12.5f); lineTo(15f, 14.8f); lineTo(10.5f, 17.1f); close()
    }

    val ReelsFilled: ImageVector = filled("ReelsFilled") {
        roundedRect(3.5f, 4f, 20.5f, 20f, 3f)
        moveTo(10.5f, 12.5f); lineTo(15f, 14.8f); lineTo(10.5f, 17.1f); close()
    }

    val Heart: ImageVector = stroked("Heart") { heartPath() }

    val HeartFilled: ImageVector = filled("HeartFilled") { heartPath() }

    val Chat: ImageVector = stroked("Chat") { chatPath() }

    val ChatFilled: ImageVector = filled("ChatFilled") { chatPath() }

    val Person: ImageVector = stroked("Person") {
        circle(12f, 8f, 3.6f)
        moveTo(4.8f, 20f)
        curveTo(4.8f, 16.4f, 8f, 14f, 12f, 14f)
        curveTo(16f, 14f, 19.2f, 16.4f, 19.2f, 20f)
    }

    val PersonFilled: ImageVector = filled("PersonFilled") {
        circle(12f, 8f, 3.6f)
        moveTo(4.8f, 20.5f)
        curveTo(4.8f, 16.4f, 8f, 14f, 12f, 14f)
        curveTo(16f, 14f, 19.2f, 16.4f, 19.2f, 20.5f)
        close()
    }

    // ---- top bar ----------------------------------------------------------

    val Search: ImageVector = stroked("Search") {
        circle(11f, 11f, 6.2f)
        moveTo(15.6f, 15.6f); lineTo(20f, 20f)
    }

    /** Two people — the Friends entry. */
    val People: ImageVector = stroked("People") {
        circle(9.5f, 8.5f, 3.2f)
        moveTo(3.2f, 19.5f)
        curveTo(3.2f, 16.3f, 6f, 14.2f, 9.5f, 14.2f)
        curveTo(13f, 14.2f, 15.8f, 16.3f, 15.8f, 19.5f)
        moveTo(16.2f, 5.8f)
        curveTo(18f, 6.2f, 19.3f, 7.7f, 19.3f, 9.5f)
        curveTo(19.3f, 11.3f, 18f, 12.8f, 16.2f, 13.2f)
        moveTo(18f, 14.8f)
        curveTo(19.9f, 15.6f, 21f, 17.2f, 21f, 19.5f)
    }

    val Bell: ImageVector = stroked("Bell") {
        moveTo(6f, 16.2f); lineTo(6f, 11f)
        curveTo(6f, 7.7f, 8.7f, 5f, 12f, 5f)
        curveTo(15.3f, 5f, 18f, 7.7f, 18f, 11f)
        lineTo(18f, 16.2f); lineTo(20f, 18.2f); lineTo(4f, 18.2f); close()
        moveTo(9.8f, 18.2f)
        curveTo(9.8f, 19.5f, 10.8f, 20.5f, 12f, 20.5f)
        curveTo(13.2f, 20.5f, 14.2f, 19.5f, 14.2f, 18.2f)
    }

    /** Four-point star — the AI assistant. */
    val Sparkle: ImageVector = stroked("Sparkle") {
        moveTo(12f, 3f); lineTo(13.9f, 9.4f); lineTo(20.3f, 11.3f)
        lineTo(13.9f, 13.2f); lineTo(12f, 19.6f); lineTo(10.1f, 13.2f)
        lineTo(3.7f, 11.3f); lineTo(10.1f, 9.4f); close()
    }

    // ---- navigation and actions ------------------------------------------

    val ChevronLeft: ImageVector = stroked("ChevronLeft") {
        moveTo(15f, 4.5f); lineTo(8f, 12f); lineTo(15f, 19.5f)
    }

    val ChevronRight: ImageVector = stroked("ChevronRight") {
        moveTo(9f, 4.5f); lineTo(16f, 12f); lineTo(9f, 19.5f)
    }

    val Close: ImageVector = stroked("Close") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }

    val Check: ImageVector = stroked("Check") {
        moveTo(4.5f, 12.5f); lineTo(9.8f, 17.8f); lineTo(19.5f, 6.6f)
    }

    val Plus: ImageVector = stroked("Plus") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(19f, 12f)
    }

    val Refresh: ImageVector = stroked("Refresh") {
        moveTo(20f, 12f)
        arcTo(8f, 8f, 0f, true, true, 17.2f, 6f)
        moveTo(20.4f, 3.2f); lineTo(20.4f, 7f); lineTo(16.6f, 7f)
    }

    val MoreHorizontal: ImageVector = filled("MoreHorizontal") {
        circle(5.5f, 12f, 1.7f)
        circle(12f, 12f, 1.7f)
        circle(18.5f, 12f, 1.7f)
    }

    val Settings: ImageVector = stroked("Settings") {
        circle(12f, 12f, 3.2f)
        // Six teeth, drawn as short spokes so the shape reads as a cog at 24dp.
        moveTo(12f, 3.2f); lineTo(12f, 6f)
        moveTo(12f, 18f); lineTo(12f, 20.8f)
        moveTo(4.4f, 7.6f); lineTo(6.8f, 9f)
        moveTo(17.2f, 15f); lineTo(19.6f, 16.4f)
        moveTo(4.4f, 16.4f); lineTo(6.8f, 15f)
        moveTo(17.2f, 9f); lineTo(19.6f, 7.6f)
    }

    val Pencil: ImageVector = stroked("Pencil") {
        moveTo(4f, 20f); lineTo(4.6f, 16f); lineTo(16.2f, 4.4f)
        lineTo(19.6f, 7.8f); lineTo(8f, 19.4f); close()
        moveTo(14.2f, 6.4f); lineTo(17.6f, 9.8f)
    }

    val Camera: ImageVector = stroked("Camera") {
        moveTo(3.5f, 8.5f); lineTo(8f, 8.5f); lineTo(9.6f, 5.8f); lineTo(14.4f, 5.8f)
        lineTo(16f, 8.5f); lineTo(20.5f, 8.5f); lineTo(20.5f, 19f); lineTo(3.5f, 19f); close()
        circle(12f, 13.6f, 3.4f)
    }

    val Send: ImageVector = stroked("Send") {
        moveTo(21f, 3.6f); lineTo(2.8f, 11.4f); lineTo(10.4f, 13.9f); lineTo(21f, 3.6f)
        moveTo(21f, 3.6f); lineTo(13.4f, 21f); lineTo(10.4f, 13.9f)
    }

    val Comment: ImageVector = stroked("Comment") { chatPath() }

    val Location: ImageVector = stroked("Location") {
        moveTo(12f, 21f)
        curveTo(12f, 21f, 19f, 15.2f, 19f, 10.2f)
        curveTo(19f, 6.3f, 15.9f, 3.2f, 12f, 3.2f)
        curveTo(8.1f, 3.2f, 5f, 6.3f, 5f, 10.2f)
        curveTo(5f, 15.2f, 12f, 21f, 12f, 21f)
        close()
        circle(12f, 10f, 2.6f)
    }

    val Link: ImageVector = stroked("Link") {
        moveTo(10f, 13.6f)
        curveTo(11.2f, 14.8f, 13.1f, 14.8f, 14.3f, 13.6f)
        lineTo(18.2f, 9.7f)
        curveTo(19.4f, 8.5f, 19.4f, 6.6f, 18.2f, 5.4f)
        curveTo(17f, 4.2f, 15.1f, 4.2f, 13.9f, 5.4f)
        lineTo(12.6f, 6.7f)
        moveTo(14f, 10.4f)
        curveTo(12.8f, 9.2f, 10.9f, 9.2f, 9.7f, 10.4f)
        lineTo(5.8f, 14.3f)
        curveTo(4.6f, 15.5f, 4.6f, 17.4f, 5.8f, 18.6f)
        curveTo(7f, 19.8f, 8.9f, 19.8f, 10.1f, 18.6f)
        lineTo(11.4f, 17.3f)
    }

    val Mail: ImageVector = stroked("Mail") {
        roundedRect(3f, 5.5f, 21f, 18.5f, 2.4f)
        moveTo(3.6f, 7f); lineTo(12f, 13f); lineTo(20.4f, 7f)
    }

    val Phone: ImageVector = stroked("Phone") {
        moveTo(7.2f, 3.6f); lineTo(10f, 3.6f); lineTo(11.4f, 8f); lineTo(9.2f, 9.6f)
        curveTo(10.2f, 12.4f, 11.6f, 13.8f, 14.4f, 14.8f)
        lineTo(16f, 12.6f); lineTo(20.4f, 14f); lineTo(20.4f, 16.8f)
        curveTo(20.4f, 18.6f, 19f, 20f, 17.2f, 20f)
        curveTo(10f, 20f, 4f, 14f, 4f, 6.8f)
        curveTo(4f, 5f, 5.4f, 3.6f, 7.2f, 3.6f)
        close()
    }

    val Calendar: ImageVector = stroked("Calendar") {
        roundedRect(3.5f, 5.5f, 20.5f, 20.5f, 2.4f)
        moveTo(3.5f, 10f); lineTo(20.5f, 10f)
        moveTo(8f, 3.4f); lineTo(8f, 7f)
        moveTo(16f, 3.4f); lineTo(16f, 7f)
    }

    val Star: ImageVector = filled("Star") {
        moveTo(12f, 3.2f); lineTo(14.7f, 9f); lineTo(21f, 9.8f); lineTo(16.4f, 14.2f)
        lineTo(17.6f, 20.4f); lineTo(12f, 17.4f); lineTo(6.4f, 20.4f); lineTo(7.6f, 14.2f)
        lineTo(3f, 9.8f); lineTo(9.3f, 9f); close()
    }

    val Info: ImageVector = stroked("Info") {
        circle(12f, 12f, 8.6f)
        moveTo(12f, 11f); lineTo(12f, 16.4f)
        moveTo(12f, 7.6f); lineTo(12f, 8.2f)
    }

    val Music: ImageVector = stroked("Music") {
        moveTo(9f, 18f); lineTo(9f, 5f); lineTo(19f, 3f); lineTo(19f, 16f)
        circle(6.6f, 18f, 2.6f)
        circle(16.6f, 16f, 2.6f)
    }

    val Image: ImageVector = stroked("Image") {
        roundedRect(3.5f, 4.5f, 20.5f, 19.5f, 2.4f)
        circle(9f, 10f, 1.8f)
        moveTo(4f, 17.5f); lineTo(9.5f, 12.5f); lineTo(14f, 16.5f)
        lineTo(17f, 13.8f); lineTo(20f, 16.6f)
    }

    val Diamond: ImageVector = stroked("Diamond") {
        moveTo(12f, 3.4f); lineTo(20.6f, 12f); lineTo(12f, 20.6f); lineTo(3.4f, 12f); close()
    }

    // ---- builders ---------------------------------------------------------

    private fun stroked(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block
            )
        }.build()

    private fun filled(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathBuilder = block)
        }.build()
}

/** Circle as two half arcs — [PathBuilder] has no oval primitive. */
private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
    close()
}

private fun PathBuilder.roundedRect(l: Float, t: Float, r: Float, b: Float, radius: Float) {
    moveTo(l + radius, t)
    lineTo(r - radius, t)
    arcTo(radius, radius, 0f, false, true, r, t + radius)
    lineTo(r, b - radius)
    arcTo(radius, radius, 0f, false, true, r - radius, b)
    lineTo(l + radius, b)
    arcTo(radius, radius, 0f, false, true, l, b - radius)
    lineTo(l, t + radius)
    arcTo(radius, radius, 0f, false, true, l + radius, t)
    close()
}

private fun PathBuilder.heartPath() {
    moveTo(12f, 20.4f)
    curveTo(12f, 20.4f, 3f, 14.8f, 3f, 9f)
    curveTo(3f, 6.2f, 5.2f, 4f, 8f, 4f)
    curveTo(9.9f, 4f, 11.3f, 5f, 12f, 6.4f)
    curveTo(12.7f, 5f, 14.1f, 4f, 16f, 4f)
    curveTo(18.8f, 4f, 21f, 6.2f, 21f, 9f)
    curveTo(21f, 14.8f, 12f, 20.4f, 12f, 20.4f)
    close()
}

private fun PathBuilder.chatPath() {
    moveTo(20.6f, 11.4f)
    curveTo(20.6f, 15.5f, 16.7f, 18.8f, 12f, 18.8f)
    curveTo(10.9f, 18.8f, 9.9f, 18.6f, 9f, 18.3f)
    lineTo(4f, 20f)
    lineTo(5.6f, 16f)
    curveTo(4.4f, 14.7f, 3.4f, 13.2f, 3.4f, 11.4f)
    curveTo(3.4f, 7.3f, 7.3f, 4f, 12f, 4f)
    curveTo(16.7f, 4f, 20.6f, 7.3f, 20.6f, 11.4f)
    close()
}
