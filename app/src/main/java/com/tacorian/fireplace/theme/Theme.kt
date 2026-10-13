package com.tacorian.fireplace.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Material 3 tonal roles around the Ember seed, so surfaces carry a warm tint instead of the baseline purple.
private val LightColorScheme = lightColorScheme(
  primary = Color(0xFFA4400E),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFFFDBCE),
  onPrimaryContainer = Color(0xFF380D00),
  secondary = Color(0xFF77574B),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFFFDBCE),
  onSecondaryContainer = Color(0xFF2C160C),
  tertiary = Color(0xFF695E2F),
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Color(0xFFF2E2A7),
  onTertiaryContainer = Color(0xFF221B00),
  background = Color(0xFFFFF8F6),
  onBackground = Color(0xFF231A16),
  surface = Color(0xFFFFF8F6),
  onSurface = Color(0xFF231A16),
  surfaceVariant = Color(0xFFF5DED6),
  onSurfaceVariant = Color(0xFF53433E),
  outline = Color(0xFF85736D),
  outlineVariant = Color(0xFFD8C2BA),
  surfaceDim = Color(0xFFE8D6D0),
  surfaceBright = Color(0xFFFFF8F6),
  surfaceContainerLowest = Color(0xFFFFFFFF),
  surfaceContainerLow = Color(0xFFFFF1EC),
  surfaceContainer = Color(0xFFFCEAE4),
  surfaceContainerHigh = Color(0xFFF6E5DE),
  surfaceContainerHighest = Color(0xFFF1DFD8),
)

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFFFFB598),
  onPrimary = Color(0xFF5B1A00),
  primaryContainer = Color(0xFF812A00),
  onPrimaryContainer = Color(0xFFFFDBCE),
  secondary = Color(0xFFE7BEAF),
  onSecondary = Color(0xFF442A20),
  secondaryContainer = Color(0xFF5D4035),
  onSecondaryContainer = Color(0xFFFFDBCE),
  tertiary = Color(0xFFD5C68E),
  onTertiary = Color(0xFF393005),
  tertiaryContainer = Color(0xFF50461A),
  onTertiaryContainer = Color(0xFFF2E2A7),
  background = Color(0xFF1A110E),
  onBackground = Color(0xFFF1DFD8),
  surface = Color(0xFF1A110E),
  onSurface = Color(0xFFF1DFD8),
  surfaceVariant = Color(0xFF53433E),
  onSurfaceVariant = Color(0xFFD8C2BA),
  outline = Color(0xFFA08D86),
  outlineVariant = Color(0xFF53433E),
  surfaceDim = Color(0xFF1A110E),
  surfaceBright = Color(0xFF42372F),
  surfaceContainerLowest = Color(0xFF140C09),
  surfaceContainerLow = Color(0xFF231A16),
  surfaceContainer = Color(0xFF271E1A),
  surfaceContainerHigh = Color(0xFF322824),
  surfaceContainerHighest = Color(0xFF3D322E),
)

@Composable
fun FireplaceTheme(
  darkTheme: Boolean,
  // Material You wallpaper colors, available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
