package app.lawnchair.ui.preferences.components.colorpreference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.layout.Chip

/**
 * Editor for a persisted Monet recipe. Unlike the fixed picker this stores the palette coordinates,
 * not today's resolved ARGB, so wallpaper/palette changes continue to affect the result.
 */
@Composable
fun PrimeDynamicColorPicker(
    initial: ColorOption.DynamicColor?,
    onApply: (ColorOption.DynamicColor) -> Unit,
) {
    val swatches = listOf("Neutral1", "Neutral2", "Accent1", "Accent2", "Accent3")
    val shades = listOf(0, 10, 20, 50, 100, 200, 300, 400, 500, 600, 650, 700, 800, 900, 950, 1000)
    var swatch by remember(initial) { mutableStateOf(initial?.swatch ?: "Accent1") }
    var shadeIndex by remember(initial) {
        mutableFloatStateOf(shades.indexOf(initial?.shade ?: 500).coerceAtLeast(0).toFloat())
    }
    var useLStar by remember(initial) { mutableStateOf(initial?.lStar != null) }
    var lStar by remember(initial) { mutableFloatStateOf((initial?.lStar ?: 50).toFloat()) }

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Text("Palette")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            swatches.forEach { value ->
                Chip(
                    label = value,
                    onClick = { swatch = value },
                    currentOffset = if (swatch == value) 0f else 1f,
                    page = 0,
                )
            }
        }

        val shade = shades[shadeIndex.toInt().coerceIn(shades.indices)]
        Text("Tonalité : $shade")
        Slider(
            value = shadeIndex,
            onValueChange = { shadeIndex = it },
            valueRange = 0f..shades.lastIndex.toFloat(),
            steps = shades.size - 2,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(
                label = "Palette",
                onClick = { useLStar = false },
                currentOffset = if (!useLStar) 0f else 1f,
                page = 0,
            )
            Chip(
                label = "Luminosité personnalisée",
                onClick = { useLStar = true },
                currentOffset = if (useLStar) 0f else 1f,
                page = 0,
            )
        }
        if (useLStar) {
            Text("Luminosité : ${lStar.toInt()}%")
            Slider(value = lStar, onValueChange = { lStar = it }, valueRange = 0f..100f)
        }

        Button(
            onClick = {
                onApply(
                    ColorOption.DynamicColor(
                        swatch = swatch,
                        shade = shade,
                        lStar = if (useLStar) lStar.toInt() else null,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text("Appliquer")
        }
    }
}
