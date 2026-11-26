package dev.opendungeon.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** A 1 dp dashed outline in [shape], drawn inside the bounds like `border`. */
public fun Modifier.dashedBorder(color: Color, shape: Shape): Modifier = drawWithContent {
    drawContent()
    val width = 1.dp.toPx()
    val inset = width / 2
    val outline = shape.createOutline(
        size.copy(width = size.width - width, height = size.height - width),
        layoutDirection,
        this,
    )
    val path = Path().apply {
        addOutline(outline)
        translate(Offset(inset, inset))
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}
