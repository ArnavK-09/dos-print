package com.dosprint.ui.components

import androidx.compose.ui.graphics.vector.ImageVector

data class UniformButton(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val emphasized: Boolean = false,
    val description: String? = null,
)
