package com.catsmoker.app.screens.editgamefiles

import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.catsmoker.app.screens.editgamefiles.model.GameType
import com.catsmoker.app.screens.editgamefiles.storage.PUBG_VARIANTS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Icon candidates only: a missing icon must never change an install dot or editor availability. */
internal fun pickerIconPackages(
    game: GameType,
    installedGames: Map<GameType, Boolean> = emptyMap()
): List<String> = when (game) {
    GameType.PUBG_GLOBAL, GameType.PUBG_KRJP, GameType.PUBG_VN,
    GameType.BGMI, GameType.PUBG_REKOO, GameType.PUBG_FLASH -> {
        // Prefer the chosen installed variant, then another installed one, not always Global.
        (listOf(game) + PUBG_VARIANTS.filter { it != game })
            .sortedByDescending { installedGames[it] == true }
            .map { PUBG_PACKAGES.getValue(it) }
    }
    GameType.NONE -> emptyList()
    else -> fileEngineeringGames.getValue(game).packages
}

/** Installed artwork stays local; absent/unreadable packages use a neutral, same-size fallback. */
@Composable
internal fun GamePickerIcon(packages: List<String>) {
    val packageManager = LocalContext.current.packageManager
    val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, packages, packageManager, sizePx) {
        value = null
        value = withContext(Dispatchers.IO) {
            packages.firstNotNullOfOrNull { packageName ->
                ensureActive()
                try {
                    packageManager.getApplicationIcon(packageName)
                        .toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                        .asImageBitmap()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                } catch (_: RuntimeException) {
                    // OEM resources or visibility restrictions must not break game selection.
                    null
                }
            }
        }
    }
    Box(
        modifier = Modifier.size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = icon
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
        } else {
            Icon(
                Icons.Default.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * The badge shown in the game list. Installed games get their real launcher icon; the rest
 * get a monogram tile, because a list of seventeen identical gamepad glyphs told the user
 * nothing and most of the list is deliberately "not installed yet".
 *
 * Monograms are derived, not bundled: two letters from the game's own short label plus an
 * accent colour, so no third-party game art ships in the APK and a new GameType needs no
 * artwork to look intentional.
 */
@Composable
internal fun GameBadge(
    game: GameType,
    installed: Boolean?,
    installedGames: Map<GameType, Boolean> = emptyMap()
) {
    if (installed == true) {
        GamePickerIcon(packages = pickerIconPackages(game, installedGames))
    } else {
        MonogramBadge(game)
    }
}

/**
 * Two letters from the short label: "Genshin Impact" -> GI, "GRID Legends" -> GL. The PUBG
 * family is the one exception: the short label starts with "PUBG" for all six variants, so
 * initials would render "PG"/"PM" and throw away the only distinctive word -> "PB".
 */
internal fun gameMonogram(game: GameType): String {
    if (game in PUBG_VARIANTS) return "PB"
    val words = game.shortLabel.split(' ', '-', ':').filter { it.isNotBlank() }
    val letters = if (words.size >= 2) {
        words[0].take(1) + words[1].take(1)
    } else {
        words.firstOrNull().orEmpty().take(2)
    }
    return letters.uppercase()
}

/**
 * Per-game accent for the monogram tile.
 *
 * A hardcoded table alone was wrong: it covered ten headline titles and dropped the other
 * twenty onto one grey, so the rail read as "some games have colours, the rest do not".
 * Brands with a recognisable colour keep an explicit entry; everything else derives a
 * stable hue from the enum name, so every game is distinguishable, adding a `GameType`
 * needs no colour decision, and the same game never changes colour between runs.
 */
internal fun gameAccent(game: GameType): Color =
    brandAccent(game) ?: Color.hsl(hue = derivedHue(game), saturation = 0.48f, lightness = 0.60f)

private fun brandAccent(game: GameType): Color? = when (game) {
    GameType.PUBG_GLOBAL, GameType.PUBG_KRJP, GameType.PUBG_VN,
    GameType.BGMI, GameType.PUBG_REKOO, GameType.PUBG_FLASH -> Color(0xFFF2A93B)
    GameType.GENSHIN_IMPACT -> Color(0xFF4CC9B0)
    GameType.PUBG_NEW_STATE -> Color(0xFF5AA9E6)
    GameType.HSR -> Color(0xFFA78BFA)
    GameType.WUWA -> Color(0xFF38BDF8)
    GameType.GRID -> Color(0xFFF97316)
    GameType.ROBLOX -> Color(0xFFEF4444)
    else -> null
}

/** Stable hue in [0, 360) from the enum name; never negative, never re-rolled per process. */
private fun derivedHue(game: GameType): Float {
    val spread = ((game.name.hashCode() % 360) + 360) % 360
    return spread.toFloat()
}

@Composable
private fun MonogramBadge(game: GameType) {
    val accent = gameAccent(game)
    Box(
        modifier = Modifier.size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = gameMonogram(game),
            color = accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
