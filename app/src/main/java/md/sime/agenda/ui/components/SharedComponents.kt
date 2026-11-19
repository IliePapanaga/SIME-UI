package md.sime.agenda.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import md.sime.agenda.ui.theme.SimeTheme

@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(14.dp),
    backgroundColor: Color = SimeTheme.colors.surface,
    borderColor: Color? = null,
    isHighlighted: Boolean = false
): Modifier {
    val colors = SimeTheme.colors
    val glassBorderBrush = when {
        borderColor != null && borderColor != colors.border -> {
            Brush.linearGradient(
                listOf(
                    borderColor.copy(alpha = 0.72f),
                    borderColor.copy(alpha = 0.25f)
                )
            )
        }
        colors.isDark -> {
            Brush.linearGradient(
                0.0f to Color.White.copy(alpha = if (isHighlighted) 0.42f else 0.18f),
                0.5f to Color.White.copy(alpha = 0.04f),
                1.0f to Color.White.copy(alpha = if (isHighlighted) 0.22f else 0.08f)
            )
        }
        else -> {
            Brush.linearGradient(
                listOf(
                    if (isHighlighted) colors.textPrimary.copy(alpha = 0.45f) else colors.border,
                    colors.border.copy(alpha = 0.7f)
                )
            )
        }
    }

    val sheenBrush = if (colors.isDark) {
        Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = if (isHighlighted) 0.09f else 0.055f),
            0.6f to Color.White.copy(alpha = 0.012f),
            1.0f to Color.White.copy(alpha = 0.03f)
        )
    } else {
        null
    }

    return this
        .clip(shape)
        .background(backgroundColor, shape)
        .let { if (sheenBrush != null) it.background(sheenBrush, shape) else it }
        .border(BorderStroke(0.75.dp, glassBorderBrush), shape)
}

@Composable
fun SimeCard(
    modifier: Modifier = Modifier,
    borderColor: Color = SimeTheme.colors.border,
    backgroundColor: Color = SimeTheme.colors.surface,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    cornerRadius: Dp = 14.dp,
    isHighlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val baseModifier = modifier
        .fillMaxWidth()
        .liquidGlass(
            shape = shape,
            backgroundColor = backgroundColor,
            borderColor = borderColor,
            isHighlighted = isHighlighted
        )
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    Column(
        modifier = baseModifier.padding(contentPadding),
        content = content
    )
}

@Composable
fun SectionHeader(
    title: String,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = SimeTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            color = colors.textMuted,
            fontSize = 11.sp,
            letterSpacing = 1.3.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (!trailing.isNullOrBlank()) {
            val displayTrailing = if (onTrailingClick != null && !trailing.endsWith("›")) {
                "$trailing ›"
            } else {
                trailing
            }
            Text(
                text = displayTrailing,
                color = if (onTrailingClick != null) colors.textSecondary else colors.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = if (onTrailingClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onTrailingClick)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                } else {
                    Modifier
                }
            )
        }
    }
}

@Composable
fun PillToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SimeTheme.colors
    val shape = RoundedCornerShape(8.dp)
    val bgColor = when {
        selected && colors.isDark -> Color.White.copy(alpha = 0.12f)
        selected && !colors.isDark -> colors.surface
        !selected && colors.isDark -> colors.surface
        else -> colors.surfaceElevated
    }
    val borderColor = when {
        selected && colors.isDark -> Color.White.copy(alpha = 0.28f)
        selected && !colors.isDark -> colors.textPrimary.copy(alpha = 0.35f)
        else -> colors.border
    }

    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = bgColor,
        border = BorderStroke(0.75.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) colors.textPrimary else colors.textMuted,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun SegmentedGlassToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SimeTheme.colors
    val outerShape = RoundedCornerShape(12.dp)
    val innerShape = RoundedCornerShape(9.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(shape = outerShape, backgroundColor = colors.surface)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val activeBg = if (colors.isDark) Color.White.copy(alpha = 0.11f) else colors.surface
            val activeBorder = if (colors.isDark) Color.White.copy(alpha = 0.24f) else colors.border
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(innerShape)
                    .then(
                        if (selected) {
                            Modifier
                                .background(activeBg, innerShape)
                                .border(BorderStroke(0.75.dp, activeBorder), innerShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp, horizontal = if (options.size >= 4) 6.dp else 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) colors.textPrimary else colors.textMuted,
                    fontSize = if (options.size >= 4) 12.sp else 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    val color = if (SimeTheme.colors.isDark) {
        Color.White.copy(alpha = 0.07f)
    } else {
        SimeTheme.colors.border
    }
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = color
    )
}

@Composable
fun VerticalHairlineDivider(modifier: Modifier = Modifier) {
    val color = if (SimeTheme.colors.isDark) {
        Color.White.copy(alpha = 0.07f)
    } else {
        SimeTheme.colors.border
    }
    Box(
        modifier = modifier
            .width(0.5.dp)
            .fillMaxHeight()
            .background(color)
    )
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(4.dp)
            .background(color, RoundedCornerShape(2.dp))
    )
}
