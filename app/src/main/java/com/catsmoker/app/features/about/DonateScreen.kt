package com.catsmoker.app.features.about

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsmoker.app.R
import com.catsmoker.app.shared.ui.components.ScreenScaffold
import com.catsmoker.app.shared.ui.components.SectionCard
import com.catsmoker.app.shared.ui.theme.CatsmokerTheme

/**
 * One crypto destination. The coin/network names stay verbatim (data, like the addresses —
 * never translated), so only the screen chrome around them is string resources.
 */
internal data class CryptoOption(
    val id: String,
    val networkName: String,
    val address: String
)

/** Donation destinations. Addresses are pinned exact by `DonateDataTest` — a typo sends funds nowhere. */
internal object DonateData {
    const val BINANCE_ID = "791299459"
    const val BINANCE_QR_URL =
        "https://app.binance.com/uni-qr/cpro/CATSMOKER?l=en&r=H8N7TAEN&uc=web_square_share_link&us=copylink"
    val crypto: List<CryptoOption> = listOf(
        CryptoOption("bitcoin", "Bitcoin (Native)", "1FCpWous8JmKBSB651u3GJmh5caWvJ6d33"),
        CryptoOption("ethereum", "Ethereum (ERC20)", "0xb813f07bce7df3c333acc33d0efe021f6c823880"),
        CryptoOption("binancecoin", "BNB (BEP20)", "0xb813f07bce7df3c333acc33d0efe021f6c823880"),
        CryptoOption("tether", "USDT (TRC20)", "TTdXcExjMTxSnM5HEpsg7mh3huPTxrmvYq"),
        CryptoOption("tether", "USDT (ERC20 / BEP20)", "0xb813f07bce7df3c333acc33d0efe021f6c823880"),
        CryptoOption("usd-coin", "USDC (ERC20 / BEP20)", "0xb813f07bce7df3c333acc33d0efe021f6c823880")
    )
}

@Composable
fun DonateRoute(onBack: () -> Unit) {
    DonateScreen(onBack = onBack)
}

@Composable
fun DonateScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    // Resolved once here, at composition in the activity locale — the Toast below fires
    // immediately on tap, so it always matches the visible language (never a cached string).
    val copiedText = stringResource(R.string.donate_copied)
    val paypalUrl = stringResource(R.string.url_paypal)
    fun copy(value: String) {
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
    }

    ScreenScaffold(
        title = stringResource(R.string.donate_title),
        subtitle = stringResource(R.string.donate_subtitle),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard {
                Column {
                    Text(
                        stringResource(R.string.donate_binance),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DonateRow(
                        label = stringResource(R.string.donate_binance_id),
                        value = DonateData.BINANCE_ID,
                        copyLabel = stringResource(R.string.donate_copy),
                        onCopy = { copy(DonateData.BINANCE_ID) }
                    )
                    DonateRow(
                        label = stringResource(R.string.donate_qr),
                        value = DonateData.BINANCE_QR_URL,
                        copyLabel = stringResource(R.string.donate_copy),
                        onCopy = { copy(DonateData.BINANCE_QR_URL) },
                        actionLabel = stringResource(R.string.donate_open),
                        onAction = { runCatching { uriHandler.openUri(DonateData.BINANCE_QR_URL) } }
                    )
                }
            }
            SectionCard {
                Column {
                    Text(
                        stringResource(R.string.donate_paypal),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DonateRow(
                        label = stringResource(R.string.donate_paypal),
                        value = paypalUrl,
                        copyLabel = stringResource(R.string.donate_copy),
                        onCopy = { copy(paypalUrl) },
                        actionLabel = stringResource(R.string.donate_open),
                        onAction = { runCatching { uriHandler.openUri(paypalUrl) } }
                    )
                }
            }
            SectionCard {
                Column {
                    Text(
                        stringResource(R.string.donate_crypto),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DonateData.crypto.forEach { option ->
                        DonateRow(
                            label = option.networkName,
                            value = option.address,
                            copyLabel = stringResource(R.string.donate_copy),
                            onCopy = { copy(option.address) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DonateRow(
    label: String,
    value: String,
    copyLabel: String,
    onCopy: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface)
            Text(
                value,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = MaterialTheme.colorScheme.primary)
            }
        }
        TextButton(onClick = onCopy) {
            Text(copyLabel, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun DonatePreview() {
    CatsmokerTheme {
        DonateScreen(onBack = {})
    }
}
