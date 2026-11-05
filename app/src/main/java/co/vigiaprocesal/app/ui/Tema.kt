package co.vigiaprocesal.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Claro = lightColorScheme(
    primary = Color(0xFF1F3A5F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E3F7),
    onPrimaryContainer = Color(0xFF0B1D33),
    secondary = Color(0xFF7A5C14),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFBE7B8),
    onSecondaryContainer = Color(0xFF2B1F00),
    background = Color(0xFFFBF9F6),
    surface = Color(0xFFFBF9F6),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFA9C7EE),
    onPrimary = Color(0xFF0B1D33),
    primaryContainer = Color(0xFF2D4B73),
    onPrimaryContainer = Color(0xFFD5E3F7),
    secondary = Color(0xFFE7C26A),
    onSecondary = Color(0xFF2B1F00),
    secondaryContainer = Color(0xFF5A4512),
    onSecondaryContainer = Color(0xFFFBE7B8),
)

@Composable
fun TemaVigia(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun coloresBarra(): TopAppBarColors {
    val c = MaterialTheme.colorScheme
    return TopAppBarDefaults.topAppBarColors(
        containerColor = c.primaryContainer,
        titleContentColor = c.onPrimaryContainer,
        navigationIconContentColor = c.onPrimaryContainer,
        actionIconContentColor = c.onPrimaryContainer,
    )
}
