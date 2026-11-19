package io.github.rude56.poemstatus.ui.theme

import androidx.compose.ui.graphics.Color

val HolePalette = listOf(
    Color(0xFFE5484D),
    Color(0xFF12A594),
    Color(0xFFF76B15),
    Color(0xFF3E63DD),
    Color(0xFF30A46C),
    Color(0xFF8E4EC6),
    Color(0xFFF5D90A),
    Color(0xFFD6409F),
)

fun holeColor(index: Int): Color = HolePalette[index % HolePalette.size]
