package com.nonverbal.mastery.design.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object Shapes {
    val large = RoundedCornerShape(20.dp)
    val card = RoundedCornerShape(14.dp)
    val chip = RoundedCornerShape(10.dp)
    val pill = RoundedCornerShape(999.dp)

    /** Hairline used everywhere a boundary is needed. Elevation is reserved for floating actions. */
    val hairline = 1.dp
}
