package com.catsmoker.app.screens.permissions

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.catsmoker.app.application.appearance.LocaleHelper
import com.catsmoker.app.screens.permissions.PermissionViewModel
import com.catsmoker.app.ui.theme.CatsmokerTheme

@Composable
fun PermissionRoute(onDone: () -> Unit) {
    val viewModel: PermissionViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val hasPreviousStep = uiState.isOnboarding && !uiState.isAppearanceStep
    BackHandler(enabled = hasPreviousStep) { viewModel.onBack() }
    val onBack: () -> Unit = {
        if (hasPreviousStep) viewModel.onBack() else dispatcher?.onBackPressed()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // A newly picked language restarts the process — the ViewModel asks, the screen
    // obeys. The current step survives it in the committed draft. A refused root
    // request is named instead of leaving the row off with no explanation.
    val rootDeniedMessage = stringResource(R.string.core_perm_root_denied)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PermissionViewModel.UiEvent.RestartApp -> LocaleHelper.restartApp(context)
                PermissionViewModel.UiEvent.RootRequestFailed ->
                    Toast.makeText(context, rootDeniedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (uiState.isAppearanceStep) {
        AppearanceStepScreen(
            themeMode = uiState.themeMode,
            languageTag = uiState.languageTag,
            onThemeChange = viewModel::onThemeModeChanged,
            onLanguageChange = viewModel::onLanguageChanged,
            onContinue = viewModel::onAppearanceContinue,
            onBack = onBack,
        )
    } else if (uiState.isAgreementStep) {
        AgreementStepScreen(
            agreed = uiState.choices.agreed,
            onAgreedChange = viewModel::onAgreedChange,
            analytics = uiState.choices.analytics,
            onAnalyticsChange = viewModel::onAnalyticsChange,
            onBack = onBack,
            onContinue = { viewModel.onAgreementContinue() },
        )
    } else {
        PermissionsListScreen(
            uiState = uiState,
            onRefresh = viewModel::refreshStates,
            onRequestRoot = viewModel::requestRootPermission,
            onRequestShizuku = viewModel::requestShizukuPermission,
            onAccessChange = viewModel::onAccessChange,
            onConfirmAccessRequest = viewModel::confirmAccessRequest,
            onBack = onBack,
            onDone = { viewModel.onDone(onDone) }
        )
    }
}


@Composable
fun AppearanceStepPreview() {
    CatsmokerTheme {
        AppearanceStepScreen(
            themeMode = AppearanceStore.ThemeMode.SYSTEM,
            languageTag = AppearanceStore.LANGUAGE_SYSTEM,
            onThemeChange = {},
            onLanguageChange = {},
            onContinue = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AgreementStepPreview() {
    CatsmokerTheme {
        AgreementStepScreen(agreed = true, onAgreedChange = {}, onContinue = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PermissionsPreview() {
    CatsmokerTheme {
        PermissionsListScreen(
            uiState = PermissionViewModel.UiState(
                choices = OnboardingState(step = OnboardingState.Step.PERMISSIONS),
                rootGranted = true,
                notifGranted = false,
                overlayGranted = true
            ),
            onRefresh = {},
            onRequestRoot = {},
            onRequestShizuku = {},
            onDone = {}
        )
    }
}
