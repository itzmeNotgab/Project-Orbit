package app.orbit.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) = Text(
    label, fontSize = 13.sp, color = if (selected) OrbitColors.Bg else OrbitColors.Text,
    modifier = Modifier.background(if (selected) OrbitColors.Accent else OrbitColors.Surface, RoundedCornerShape(16.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
)

@Composable
fun Btn(label: String, enabled: Boolean = true, onClick: () -> Unit) = Text(
    label, fontSize = 14.sp, color = if (enabled) OrbitColors.Accent else OrbitColors.Muted,
    modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp),
)

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().background(OrbitColors.Surface, RoundedCornerShape(8.dp)).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) { Text(title, color = OrbitColors.Muted, fontSize = 12.sp); content() }
