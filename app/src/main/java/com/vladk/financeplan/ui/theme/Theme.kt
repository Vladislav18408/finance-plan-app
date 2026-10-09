package com.vladk.financeplan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.vladk.financeplan.data.Group
import com.vladk.financeplan.data.Kind

private val Light = lightColorScheme(
    primary = Color(0xFF1E6B52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1D4),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4C6358),
    secondaryContainer = Color(0xFFCEE9DA),
    onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF3E6374),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF6FBF6),
    surface = Color(0xFFF6FBF6),
    surfaceContainerLow = Color(0xFFF0F5F0),
    surfaceContainer = Color(0xFFEAEFEA),
    surfaceContainerHigh = Color(0xFFE4EAE5),
    surfaceVariant = Color(0xFFDBE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outlineVariant = Color(0xFFBFC9C1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF98D5B8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513C),
    onPrimaryContainer = Color(0xFFB4F1D4),
    secondary = Color(0xFFB3CCBF),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFFA6CCDF),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    background = Color(0xFF0F1512),
    surface = Color(0xFF0F1512),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    outlineVariant = Color(0xFF404943),
)

@Composable
fun FinanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** One steady color per kind, readable on light and dark backgrounds. */
@Composable
fun kindColor(kind: Kind): Color {
    val dark = isSystemInDarkTheme()
    return when (kind) {
        Kind.NEEDS -> if (dark) Color(0xFF8AB4F8) else Color(0xFF2F62B8)
        Kind.WANTS -> if (dark) Color(0xFFF2A65A) else Color(0xFFC0610F)
        Kind.SAVINGS -> if (dark) Color(0xFF7DD3A8) else Color(0xFF1E8A5A)
        Kind.INVESTMENTS -> if (dark) Color(0xFFC6A4F2) else Color(0xFF7346B5)
    }
}

/**
 * Categorical chart palette (blue, orange, aqua, yellow, magenta, green, violet, red), validated for
 * colour-blind separation in this fixed order. Light and dark are separate steps of the same hues.
 */
private val SeriesLight = listOf(
    Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
    Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF4A3AA7), Color(0xFFE34948),
)
private val SeriesDark = listOf(
    Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
    Color(0xFFD55181), Color(0xFF008300), Color(0xFF9085E9), Color(0xFFE66767),
)

@Composable
fun seriesColor(slot: Int): Color = (if (isSystemInDarkTheme()) SeriesDark else SeriesLight)[slot % 8]

/** Each spending group keeps the same colour in every chart (colour follows the entity). */
@Composable
fun groupColor(group: Group): Color = when (group) {
    Group.HOUSING -> seriesColor(0)
    Group.FOOD -> seriesColor(1)
    Group.TRANSPORT -> seriesColor(2)
    Group.HEALTH -> seriesColor(3)
    Group.LIFESTYLE -> seriesColor(4)
    Group.EDUCATION -> seriesColor(5)
    Group.IRREGULAR -> seriesColor(6)
    Group.OTHER -> chartMuted()
    Group.SAVINGS -> kindColor(Kind.SAVINGS)
    Group.INVESTMENTS -> kindColor(Kind.INVESTMENTS)
}

/** Recessive chart chrome: gridlines, axis text, "previous period" lines. */
@Composable
fun chartGrid(): Color = if (isSystemInDarkTheme()) Color(0xFF2C2C2A) else Color(0xFFE1E0D9)

@Composable
fun chartMuted(): Color = Color(0xFF898781)

/** Green for good, red for over budget. */
@Composable
fun goodColor(): Color = if (isSystemInDarkTheme()) Color(0xFF7DD3A8) else Color(0xFF1E8A5A)

@Composable
fun badColor(): Color = MaterialTheme.colorScheme.error
