package com.catsmoker.app.features.gamingtools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.catsmoker.app.R
import com.catsmoker.app.features.gamingtools.tools.firewall.VpnFirewall
import com.catsmoker.app.shared.data.model.GameInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Full-only consent, state and operations: Play never generates a binding for this controller. */
@HiltViewModel
class VpnControlsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firewall: VpnFirewall
) : ViewModel() {
    val state = firewall.state
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _consent = Channel<Intent>(Channel.BUFFERED)
    val consent = _consent.receiveAsFlow()
    private val _notices = Channel<String>(Channel.BUFFERED)
    val notices = _notices.receiveAsFlow()

    fun setEnabled(enabled: Boolean, games: List<GameInfo>) {
        if (_busy.value) return
        if (!enabled) {
            operate {
                firewall.stop()
                _notices.send(context.getString(R.string.gt_vm_vpn_unblocked))
            }
            return
        }
        when (val permission = firewall.consent()) {
            VpnFirewall.Consent.Granted -> start(games)
            is VpnFirewall.Consent.Required -> viewModelScope.launch { _consent.send(permission.intent) }
            is VpnFirewall.Consent.Unknown -> viewModelScope.launch {
                _notices.send(context.getString(R.string.gt_vm_vpn_unknown, permission.reason))
            }
        }
    }

    fun onConsentResult(granted: Boolean, games: List<GameInfo>) {
        if (granted) start(games) else viewModelScope.launch {
            _notices.send(context.getString(R.string.gt_vm_vpn_no))
        }
    }

    fun refresh() {
        if (state.value.running && firewall.systemReportsVpn() == false) operate { firewall.stop() }
    }

    private fun start(games: List<GameInfo>) = operate {
        firewall.start(games.map { it.packageName })?.let { _notices.send(it) }
    }

    private fun operate(block: suspend () -> Unit) {
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }
}

@Composable
internal fun BackgroundNetworkControls(
    games: List<GameInfo>,
    viewModel: VpnControlsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onConsentResult(it.resultCode == Activity.RESULT_OK, games)
    }
    LaunchedEffect(viewModel) {
        viewModel.consent.collect {
            try {
                launcher.launch(it)
            } catch (_: Exception) {
                viewModel.onConsentResult(false, games)
            }
        }
    }
    LaunchedEffect(viewModel, context) {
        viewModel.notices.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    NetworkBlockSwitch(
        title = stringResource(R.string.gt_net_vpn_title),
        subtitle = stringResource(R.string.gt_net_vpn_sub),
        checked = state.running,
        enabled = !busy,
        busy = busy,
        onCheckedChange = { viewModel.setEnabled(it, games) }
    )
    Text(
        text = when {
            state.running && state.blockedCount > 0 -> stringResource(R.string.gt_net_vpn_on_count, state.blockedCount)
            state.running -> stringResource(R.string.gt_net_on)
            else -> stringResource(R.string.gt_net_off)
        },
        fontSize = 11.sp,
        color = if (state.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
    state.lastError?.let {
        Text(stringResource(R.string.gt_net_vpn_error, it), fontSize = 11.sp, color = Color(0xFFEF9A9A), lineHeight = 15.sp)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
