package com.mindapps.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A perforated (dotted) horizontal divider following Mudita's design style.
 * Uses small circles/dots similar to Mudita's dotted chevron icons.
 *
 * @param modifier The modifier to apply to this divider
 * @param dotSize The diameter of each dot
 * @param color The color of the dots
 * @param spacing The spacing between dot centers
 */
@Composable
fun PerforatedDivider(
    modifier: Modifier = Modifier,
    dotSize: Dp = 1.5.dp,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    spacing: Dp = 4.dp
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(dotSize)
    ) {
        val dotRadius = dotSize.toPx() / 2
        val spacingPx = spacing.toPx()
        val centerY = size.height / 2

        var x = dotRadius
        while (x < size.width) {
            drawCircle(
                color = color,
                radius = dotRadius,
                center = Offset(x, centerY)
            )
            x += spacingPx
        }
    }
}
