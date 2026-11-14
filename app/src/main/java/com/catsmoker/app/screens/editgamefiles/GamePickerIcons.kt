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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
