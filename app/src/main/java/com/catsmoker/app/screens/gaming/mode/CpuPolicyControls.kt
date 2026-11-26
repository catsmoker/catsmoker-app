package com.catsmoker.app.screens.gaming.mode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catsmoker.app.R
import java.text.NumberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Direct app-UID reads on demand; this surface never probes permission by writing a CPU node. */
@Composable
internal fun CpuPolicyControls() {
    var refresh by remember { mutableIntStateOf(0) }
    val policies by produceState<List<KernelInfo.CpuPolicyDetails>?>(null, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { KernelInfo.readPolicyDetails() }.getOrDefault(emptyList()) }
    }
    val unknown = stringResource(R.string.gt_cpu_unreadable)
    val number = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 } }
    fun mhz(khz: Long?): String = khz?.let { "${number.format(it / 1000.0)} MHz" } ?: unknown
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.gt_cpu_readonly), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.gt_cpu_refresh)) }
        val read = policies
        when {
            read == null -> Text(stringResource(R.string.gt_cpu_loading))
            read.isEmpty() -> Text(stringResource(R.string.gt_cpu_no_policies))
            else -> read.forEach { policy ->
                HorizontalDivider()
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(policy.scaling.name, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.gt_cpu_driver_governor, policy.driver ?: unknown,
                            policy.scaling.governor ?: unknown), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.gt_cpu_cpus,
                            policy.relatedCpus.joinToString().ifEmpty { unknown },
                            policy.affectedCpus.joinToString().ifEmpty { unknown }), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.gt_cpu_limits, mhz(policy.scaling.minKhz),
                            mhz(policy.scaling.maxKhz), mhz(policy.scaling.curKhz)), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.gt_cpu_hardware_limits, mhz(policy.hardwareMinKhz),
                            mhz(policy.hardwareMaxKhz)), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.gt_cpu_frequency_table,
                            policy.frequenciesKhz.joinToString { mhz(it) }.ifEmpty { unknown }), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.gt_cpu_governors,
                            policy.governors.joinToString().ifEmpty { unknown }), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
