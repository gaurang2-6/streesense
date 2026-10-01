package com.stresssense.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * StressSense shape system.
 * Uses generous rounded corners (20dp-32dp) for a soft, modern look.
 */
val StressSenseShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(32.dp)
)

// Custom shapes for specific components
val CardShape = RoundedCornerShape(24.dp)
val ButtonShape = RoundedCornerShape(16.dp)
val ChipShape = RoundedCornerShape(12.dp)
val BottomSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
val BottomNavShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
