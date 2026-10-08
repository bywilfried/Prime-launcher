package app.lawnchair.ui.preferences.components.colorpreference

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HdrAuto
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.isSelectedThemeDark

@Composable
fun <T> ColorDot(
    entry: ColorPreferenceEntry<T>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val colorLight = entry.lightColor(context)
    val colorDark = entry.darkColor(context)

    val color = if (isSelectedThemeDark) colorDark else colorLight

    if (entry.value == app.lawnchair.theme.color.ColorOption.IconColor) {
        IconColorDot(modifier)
    } else if (colorLight != 0) {
        ColorDot(
            color = Color(color),
            modifier = modifier,
        )
    } else {
        DefaultColorDot(modifier = modifier)
    }
}

@Composable
private fun ColorDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color = color)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
    )
}

/** Symbolic preview: automatic contrast is per badge, never a single fixed color. */
@Composable
fun ContrastColorDot(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier.size(30.dp).clip(CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
    ) {
        drawRect(Color.Black, topLeft = Offset.Zero, size = Size(size.width / 2f, size.height))
        drawRect(Color.White, topLeft = Offset(size.width / 2f, 0f),
            size = Size(size.width / 2f, size.height))
    }
}

@Composable
fun IconColorDot(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier.size(30.dp).clip(CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
    ) {
        val colors = listOf(Color(0xFFED9A31), Color(0xFF3971C8),
            Color(0xFF40A878), Color(0xFFB74983))
        colors.forEachIndexed { index, color ->
            drawRect(color, topLeft = Offset((index % 2) * size.width / 2f,
                (index / 2) * size.height / 2f),
                size = Size(size.width / 2f, size.height / 2f))
        }
    }
}

@Composable
fun DefaultColorDot(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color = MaterialTheme.colorScheme.surfaceVariant)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.HdrAuto,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
