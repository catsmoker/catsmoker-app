package com.catsmoker.app.screens.permissions

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsmoker.app.R
import com.catsmoker.app.application.appearance.AppearanceStore
import com.catsmoker.app.screens.permissions.LanguagePills
import com.catsmoker.app.screens.permissions.OnboardingFooterButton
import com.catsmoker.app.screens.permissions.OnboardingScaffold
import com.catsmoker.app.screens.permissions.OnboardingSectionHeader
import com.catsmoker.app.screens.permissions.ThemeModeCards

/**
 * Step one: theme + language.
 *
 * The screen is a two-zone composition rather than a settings card: "01 Theme" is a row of
 * live miniatures, "02 Language" is a wrapping pill field. Both use the same numbered
 * section heading and the same brand accent, so they read as one setup surface. The
 * Continue action owns the bottom edge, and the whole step is sized to fit without
 * scrolling — the sections spread into the available height instead of stacking past it.
 */
@Composable
fun AppearanceStepScreen(
    themeMode: AppearanceStore.ThemeMode,
    languageTag: String,
    onThemeChange: (AppearanceStore.ThemeMode) -> Unit,
    onLanguageChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit = {},
) {
    OnboardingScaffold(
        step = 0,
        showBack = false,
        onBack = onBack,
        title = stringResource(R.string.sys_first_run_title),
        subtitle = stringResource(R.string.sys_first_run_subtitle),
        // On a 640 dp-tall phone the two-line subtitle is what pushes the second language
        // row under the footer; the section hints already explain both choices.
        hideSubtitleWhenCompact = true,
        footer = {
            OnboardingFooterButton(
                label = stringResource(R.string.sys_continue),
                onClick = onContinue
            )
        }
    ) { compact ->
        Column(modifier = Modifier.fillMaxSize()) {
            // The theme board takes every spare dp; the language pills keep their natural
            // height at the bottom. That keeps the step balanced on tall phones without
            // turning the gap between the two sections into a dead band.
            Column(modifier = Modifier.weight(1f)) {
                OnboardingSectionHeader(index = "01", label = stringResource(R.string.sys_theme_title))
                Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
                ThemeModeCards(
                    selected = themeMode,
                    onSelect = onThemeChange,
                    compact = compact,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.onb_theme_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(if (compact) 10.dp else 16.dp))

            Column {
                OnboardingSectionHeader(index = "02", label = stringResource(R.string.sys_language_title))
                Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
                LanguagePills(
                    selectedTag = languageTag,
                    onSelect = onLanguageChange,
                    compact = compact
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.onb_language_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
