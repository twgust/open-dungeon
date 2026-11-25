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
        moveTo(19f, 12f); horizontalLineTo(5f)
        moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
    }

    public val Next: ImageVector = stroked("Next") {
        moveTo(5f, 12f); horizontalLineTo(19f)
        moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
    }

    public val Close: ImageVector = stroked("Close") {
        moveTo(18f, 6f); lineTo(6f, 18f)
        moveTo(6f, 6f); lineTo(18f, 18f)
    }

    public val Pen: ImageVector = stroked("Pen") {
        moveTo(4f, 20f); horizontalLineTo(8f); lineTo(19f, 9f); lineTo(15f, 5f); lineTo(4f, 16f); close()
        moveTo(13.5f, 6.5f); lineTo(17.5f, 10.5f)
    }

    /** A die showing three, for the step's roll. */
    public val Die: ImageVector = die("Die", pips = listOf(9f to 9f, 12f to 12f, 15f to 15f))

    /** A die showing two, for rolling one line. */
    public val SmallDie: ImageVector = die("SmallDie", pips = listOf(9f to 9f, 15f to 15f))

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
                moveTo(7.5f, 4f); horizontalLineTo(16.5f)
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
