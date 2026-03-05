/*
 * This file is part of GNU Taler
 * (C) 2026 Taler Systems S.A.
 *
 * GNU Taler is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3, or (at your option) any later version.
 *
 * GNU Taler is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * GNU Taler; see the file COPYING.  If not, see <http://www.gnu.org/licenses/>
 */

package net.taler.merchantpos.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PosLightColorScheme = lightColorScheme(
    primary = Color(0xFF0042B3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3DEFF),
    onPrimaryContainer = Color(0xFF00134A),
    inversePrimary = Color(0xFFB4C5FF),
    secondary = Color(0xFF586A88),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E3F9),
    onSecondaryContainer = Color(0xFF111C2B),
    tertiary = Color(0xFF338AF0),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD1E4FF),
    onTertiaryContainer = Color(0xFF001C39),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFDFDFF),
    onBackground = Color(0xFF1A1C1F),
    surface = Color(0xFFFDFDFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE0E3EC),
    onSurfaceVariant = Color(0xFF45474F),
    outline = Color(0xFF767880),
    outlineVariant = Color(0xFFC4C6D0),
    inverseSurface = Color(0xFF2C2F3A),
    inverseOnSurface = Color(0xFFF0F2FF),
    primaryFixed = Color(0xFFD3DEFF),
    onPrimaryFixed = Color(0xFF00134A),
    primaryFixedDim = Color(0xFFB4C5FF),
    onPrimaryFixedVariant = Color(0xFF00379C),
    secondaryFixed = Color(0xFFD9E3F9),
    onSecondaryFixed = Color(0xFF111C2B),
    secondaryFixedDim = Color(0xFFB0BDD3),
    onSecondaryFixedVariant = Color(0xFF445670),
    tertiaryFixed = Color(0xFFD1E4FF),
    onTertiaryFixed = Color(0xFF001C39),
    tertiaryFixedDim = Color(0xFFA2CDFF),
    onTertiaryFixedVariant = Color(0xFF0C5EA5),
    surfaceDim = Color(0xFFDADDE5),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F7FC),
    surfaceContainer = Color(0xFFF0F2F7),
    surfaceContainerHigh = Color(0xFFEAECEF),
    surfaceContainerHighest = Color(0xFFE3E6EB),
    surfaceTint = Color(0xFF0042B3),
    scrim = Color(0xFF000000),
)

private val PosDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF0042B3),
    onPrimaryContainer = Color(0xFFE5EBFF),
    inversePrimary = Color(0xFF2756C7),
    secondary = Color(0xFFA4C9FF),
    onSecondary = Color(0xFF00315D),
    secondaryContainer = Color(0xFF72A3E5),
    onSecondaryContainer = Color(0xFF003869),
    tertiary = Color(0xFF8DD1E5),
    onTertiary = Color(0xFF003641),
    tertiaryContainer = Color(0xFF166577),
    onTertiaryContainer = Color(0xFF9CE0F5),
    error = Color(0xFFFFB4AA),
    onError = Color(0xFF690003),
    errorContainer = Color(0xFFB3261E),
    onErrorContainer = Color(0xFFFFCBC4),
    background = Color(0xFF11131A),
    onBackground = Color(0xFFE2E2EB),
    surface = Color(0xFF11131A),
    onSurface = Color(0xFFE5E2E1),
    surfaceVariant = Color(0xFF45474B),
    onSurfaceVariant = Color(0xFFC6C6CB),
    outline = Color(0xFF8F9095),
    outlineVariant = Color(0xFF45474B),
    inverseSurface = Color(0xFFE5E2E1),
    inverseOnSurface = Color(0xFF313030),
    primaryFixed = Color(0xFFDBE1FF),
    onPrimaryFixed = Color(0xFF00174B),
    primaryFixedDim = Color(0xFFB4C5FF),
    onPrimaryFixedVariant = Color(0xFF003EA8),
    secondaryFixed = Color(0xFFD4E3FF),
    onSecondaryFixed = Color(0xFF001C39),
    secondaryFixedDim = Color(0xFFA4C9FF),
    onSecondaryFixedVariant = Color(0xFF004883),
    tertiaryFixed = Color(0xFFAFECFF),
    onTertiaryFixed = Color(0xFF001F27),
    tertiaryFixedDim = Color(0xFF8DD1E5),
    onTertiaryFixedVariant = Color(0xFF004E5D),
    surfaceDim = Color(0xFF11131A),
    surfaceBright = Color(0xFF3A3939),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF1C1B1B),
    surfaceContainer = Color(0xFF201F1F),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF353434),
    surfaceTint = Color(0xFFB4C5FF),
    scrim = Color(0xFF000000),
)

@Composable
fun PosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PosDarkColorScheme else PosLightColorScheme,
        typography = Typography(),
        content = content,
    )
}
