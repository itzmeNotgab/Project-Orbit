package app.orbit.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = clickable(onClick = onClick)
