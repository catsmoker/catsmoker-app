package com.catsmoker.app.features.about

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
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
internal object DonateData {    const val BINANCE_ID = "791299459"
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

/** Brand tints for the donate icons. Fixed hues, not theme roles, so each coin reads as itself. */
private val BinanceYellow = Color(0xFFF0B90B)
private val PayPalBlue = Color(0xFF0079C1)
private val BitcoinOrange = Color(0xFFF7931A)
private val EthereumBlue = Color(0xFF627EEA)
private val TetherGreen = Color(0xFF26A17B)
private val UsdcBlue = Color(0xFF2775CA)

/**
 * Icon for one [CryptoOption.id]. The two USDT networks share the Tether icon.
 * Pinned by `DonateDataTest.everyCoinHasItsOwnIcon` — a new coin without a
 * mapping fails there instead of shipping a row with a missing icon.
 */
internal fun cryptoIconFor(id: String): Int = when (id) {
    "bitcoin" -> R.drawable.ic_bitcoin
    "ethereum" -> R.drawable.ic_ethereum
    "binancecoin" -> R.drawable.ic_binancecoin
    "tether" -> R.drawable.ic_tether
    "usd-coin" -> R.drawable.ic_usdc
    else -> R.drawable.ic_usdc
}

/** Tint pairing [cryptoIconFor]. New coins land here together with their icon. */
internal fun cryptoTintFor(id: String): Color = when (id) {
    "bitcoin" -> BitcoinOrange
    "ethereum" -> EthereumBlue
    "binancecoin" -> BinanceYellow
    "tether" -> TetherGreen
    "usd-coin" -> UsdcBlue
    else -> UsdcBlue
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
                        onCopy = { copy(DonateData.BINANCE_ID) },
                        iconRes = R.drawable.ic_id_card,
                        iconTint = BinanceYellow
                    )
                    DonateRow(
                        label = stringResource(R.string.donate_qr),
                        value = DonateData.BINANCE_QR_URL,
                        copyLabel = stringResource(R.string.donate_copy),
                        onCopy = { copy(DonateData.BINANCE_QR_URL) },
                        actionLabel = stringResource(R.string.donate_open),
                        onAction = { runCatching { uriHandler.openUri(DonateData.BINANCE_QR_URL) } },
                        iconRes = R.drawable.ic_qr,
                        iconTint = BinanceYellow
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
                        onAction = { runCatching { uriHandler.openUri(paypalUrl) } },
                        iconRes = R.drawable.ic_paypal,
                        iconTint = PayPalBlue
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
                            onCopy = { copy(option.address) },
                            iconRes = cryptoIconFor(option.id),
                            iconTint = cryptoTintFor(option.id)
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
    onAction: (() -> Unit)? = null,
    iconRes: Int? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    // Narrow phones stack the buttons under the address: icon + 42-char address +
    // two buttons never fit one 360.dp row without squeezing. Tablets keep one row.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (maxWidth < 480.dp) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonateRowIcon(iconRes, iconTint)
                    Spacer(modifier = Modifier.width(12.dp))
                    DonateRowTexts(label, value, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    DonateRowButtons(actionLabel, onAction, copyLabel, onCopy)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DonateRowIcon(iconRes, iconTint)
                Spacer(modifier = Modifier.width(12.dp))
                DonateRowTexts(label, value, Modifier.weight(1f))
                DonateRowButtons(actionLabel, onAction, copyLabel, onCopy)
            }
        }
    }
}

/** Leading brand mark shared by both DonateRow layouts (decorative, no description). */
@Composable
private fun DonateRowIcon(iconRes: Int?, iconTint: Color) {
    if (iconRes != null) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun DonateRowTexts(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurface)
        Text(
            value,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DonateRowButtons(
    actionLabel: String?,
    onAction: (() -> Unit)?,
    copyLabel: String,
    onCopy: () -> Unit
) {
    if (actionLabel != null && onAction != null) {
        TextButton(onClick = onAction) {
            Text(actionLabel, color = MaterialTheme.colorScheme.primary)
        }
    }
    TextButton(onClick = onCopy) {
        Text(copyLabel, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun DonatePreview() {
    CatsmokerTheme {
        DonateScreen(onBack = {})
    }
}
