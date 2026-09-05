package com.example.uniquepersnchlg.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Generously rounded throughout - matches the rounded-square app-icon / pill-button language
// seen in the brand reference (rounded icon badge, soft card shapes).
val FaceCollageShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)
