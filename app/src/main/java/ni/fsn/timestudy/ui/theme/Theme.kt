package ni.fsn.timestudy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFF0A6E70),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F1F0),
    onPrimaryContainer = Color(0xFF063F40),
    secondary = Color(0xFF335D7E),
    background = Color(0xFFF6F8FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF2F6),
    outline = Color(0xFFD5DCE4),
    error = Color(0xFFBA1A1A)
)

@Composable
fun TimeStudyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
