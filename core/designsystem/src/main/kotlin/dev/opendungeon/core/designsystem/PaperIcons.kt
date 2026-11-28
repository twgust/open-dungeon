// Path data is coordinates on the icon grid, not tunable values.
@file:Suppress("MagicNumber")

package dev.opendungeon.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Thin stroked icons on a 24 unit grid, tinted by whoever draws them. */
public object PaperIcons {
    public val Back: ImageVector = stroked("Back") {
        moveTo(19f, 12f)
        horizontalLineTo(5f)
        moveTo(11f, 6f)
        lineTo(5f, 12f)
        lineTo(11f, 18f)
    }

    public val Next: ImageVector = stroked("Next") {
        moveTo(5f, 12f)
        horizontalLineTo(19f)
        moveTo(13f, 6f)
        lineTo(19f, 12f)
        lineTo(13f, 18f)
    }

    public val Close: ImageVector = stroked("Close") {
        moveTo(18f, 6f)
        lineTo(6f, 18f)
        moveTo(6f, 6f)
        lineTo(18f, 18f)
    }

    public val Pen: ImageVector = stroked("Pen") {
        moveTo(4f, 20f)
        horizontalLineTo(8f)
        lineTo(19f, 9f)
        lineTo(15f, 5f)
        lineTo(4f, 16f)
        close()
        moveTo(13.5f, 6.5f)
        lineTo(17.5f, 10.5f)
    }

    /** A wand with sparks, for asking a model to write. */
    public val Wand: ImageVector = stroked("Wand") {
        moveTo(4f, 20f)
        lineTo(14.5f, 9.5f)
        moveTo(13f, 8f)
        lineTo(16f, 11f)
        moveTo(18f, 3f)
        verticalLineTo(6f)
        moveTo(16.5f, 4.5f)
        horizontalLineTo(19.5f)
        moveTo(20.5f, 9f)
        verticalLineTo(11f)
        moveTo(19.5f, 10f)
        horizontalLineTo(21.5f)
        moveTo(11f, 3.5f)
        verticalLineTo(5f)
        moveTo(10.25f, 4.25f)
        horizontalLineTo(11.75f)
    }

    /** A filled square, for stopping what runs. */
    public val Stop: ImageVector = ImageVector.Builder("Stop", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7.5f, 5f)
            horizontalLineTo(16.5f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 19f, y1 = 7.5f)
            verticalLineTo(16.5f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 16.5f, y1 = 19f)
            horizontalLineTo(7.5f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 5f, y1 = 16.5f)
            verticalLineTo(7.5f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 7.5f, y1 = 5f)
            close()
        }
    }.build()

    /** A die showing three, for the step's roll. */
    public val Die: ImageVector = die("Die", pips = listOf(9f to 9f, 12f to 12f, 15f to 15f))

    /** A die showing two, for rolling one line. */
    public val SmallDie: ImageVector = die("SmallDie", pips = listOf(9f to 9f, 15f to 15f))

    /** A chili with its stem, filled: one unit of heat on a desire scale. */
    public val Chili: ImageVector = ImageVector.Builder("Chili", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(13.4f, 7.6f)
            curveTo(16.4f, 8.2f, 17.4f, 11.2f, 15.8f, 14.8f)
            curveTo(14.2f, 18.4f, 10.8f, 20.8f, 6.6f, 21.2f)
            curveTo(8.8f, 18.8f, 9.8f, 16f, 9.8f, 13.2f)
            curveTo(9.8f, 10.4f, 11f, 8f, 13.4f, 7.6f)
            close()
            moveTo(10f, 9.4f)
            curveTo(10.2f, 7.4f, 12.2f, 6.2f, 14.4f, 6.6f)
            curveTo(15.6f, 6.8f, 16.4f, 7.6f, 16.6f, 8.8f)
            curveTo(15.2f, 7.8f, 12.4f, 7.8f, 10f, 9.4f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(13.4f, 6.8f)
            curveTo(13.4f, 5f, 14.2f, 3.6f, 15.8f, 3f)
        }
    }.build()

    /** A filled drop: one unit of harm on a violence scale. */
    public val Drop: ImageVector = ImageVector.Builder("Drop", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 3.2f)
            curveTo(12f, 3.2f, 6f, 10.2f, 6f, 14.6f)
            arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 18f, y1 = 14.6f)
            curveTo(18f, 10.2f, 12f, 3.2f, 12f, 3.2f)
            close()
        }
    }.build()

    private fun stroked(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = pathBuilder,
            )
        }.build()

    private fun die(name: String, pips: List<Pair<Float, Float>>): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.3f) {
                moveTo(7.5f, 4f)
                horizontalLineTo(16.5f)
                arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 20f, y1 = 7.5f)
                verticalLineTo(16.5f)
                arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 16.5f, y1 = 20f)
                horizontalLineTo(7.5f)
                arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 4f, y1 = 16.5f)
                verticalLineTo(7.5f)
                arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 7.5f, y1 = 4f)
                close()
            }
            pips.forEach { (x, y) ->
                path(fill = SolidColor(Color.Black)) {
                    moveTo(x - 1f, y)
                    arcTo(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = x + 1f, y1 = y)
                    arcTo(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = x - 1f, y1 = y)
                    close()
                }
            }
        }.build()
}
