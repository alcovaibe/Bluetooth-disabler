package com.pulse.bluetoothdisable.cover

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Theme-tinted, transparent vector artwork matching the supplied Album, Trash and Video references. */
internal object CoverIcons {
    val Album: ImageVector = ImageVector.Builder("CoverAlbum", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = addPathNodes("M5.5,1.5 C4.1,1.5 3,2.6 3,4 V21.5 Q3,22.5 4,22.5 H17 Q18,22.5 18,21.5 V19.5 H20 Q21,19.5 21,18.5 V2.5 Q21,1.5 20,1.5 Z M5.5,3 H19.5 V18 H18 V5.5 Q18,4.5 17,4.5 H5.5 C4.45,4.5 4.45,3 5.5,3 Z M9.75,10.5 A1.5,1.5 0,1 1,6.75 10.5 A1.5,1.5 0,1 1,9.75 10.5 Z M5.4,16.95 L7.3,14.15 Q7.75,13.3 8.6,13.8 L9.65,14.45 L11.85,12.25 Q12.55,11.6 13.05,12.45 L15.6,16.85 Q16.2,18 14.85,18 H6.45 Q4.85,18 5.4,16.95 Z"),
            fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd,
        )
    }.build()

    val Trash: ImageVector = ImageVector.Builder("CoverTrash", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = addPathNodes("M9,6 V4.8 Q9,3.5 10.5,3.5 H13.5 Q15,3.5 15,4.8 V6 M5.8,6 H18.2 C20.3,6 21,7.5 21,9.5 H3 C3,7.5 3.7,6 5.8,6 Z M5,9.5 L6.2,22 H17.8 L19,9.5 M8,11.5 L8.8,19.4 M12,11.5 V19.4 M16,11.5 L15.2,19.4"),
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

    val Video: ImageVector = ImageVector.Builder("CoverVideo", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = addPathNodes("M24,12 A12,12 0,1 1,0 12 A12,12 0,1 1,24 12 Z M10.1,6.1 Q8.75,5.25 8.75,6.8 V17.2 Q8.75,18.75 10.1,17.9 L17.7,12.85 Q18.8,12 17.7,11.15 Z"),
            fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd,
        )
    }.build()
}
