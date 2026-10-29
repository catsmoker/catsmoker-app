package com.catsmoker.app.features.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.catsmoker.app.R

/**
 * Escape hatch for users who really do not want ads. Flip to `false` to hide
 * "Turn off ads anyway" — donating then becomes the only way to stop ads.
 */
const val ALLOW_TURN_OFF_ANYWAY = true

/**
 * Funny last line of defence when the free "Enable Ads" switch is turned off.
 *
 * The switch itself is not touched here: the caller keeps ads ON and only applies the change
 * through [SettingsViewModel.onAdsTurnOffAnyway]. Dismissing by tapping outside or pressing back
 * counts as "Keep ads" (same callback), so the preference can never change by accident.
 *
 * "Donate" is a plain callback into the app's own navigation graph ([com.catsmoker.app.system.navigation.Routes.DONATE]);
 * this dialog never leaves the app and never opens a browser.
 */
@Composable
fun AdsDonationDialog(
    onDonate: () -> Unit,
    onKeepAds: () -> Unit,
    onTurnOffAnyway: () -> Unit
) {
    // A single 300 ms shake on appearance: alive, but it settles so the dialog (and
    // accessibility tooling) can go idle instead of animating forever.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        shake.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 300
                -1f at 60
                1f at 120
                -0.6f at 180
                0.6f at 240
                0f at 300
            }
        )
    }

    val catDescription = stringResource(R.string.sys_ads_guilt_cat_desc)
    val donateLabel = stringResource(R.string.sys_ads_guilt_donate)
    val keepLabel = stringResource(R.string.sys_ads_guilt_keep)
    val turnOffLabel = stringResource(R.string.sys_ads_guilt_turn_off_anyway)

    Dialog(onDismissRequest = onKeepAds) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Raster meme cutout (drawable-nodpi, transparent background) rather than a
                // tinted vector: it must keep its own colours, and 96dp is drawn from the
                // 512px source without density scaling.
                Image(
                    painter = painterResource(R.drawable.ic_cat_sad),
                    contentDescription = catDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(96.dp)
                        .offset(y = (shake.value * 6f).dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.sys_ads_guilt_title),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.sys_ads_guilt_message),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDonate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .semantics { contentDescription = donateLabel }
                    ) {
                        Text(donateLabel)
                    }

                    OutlinedButton(
                        onClick = onKeepAds,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .semantics { contentDescription = keepLabel }
                    ) {
                        Text(keepLabel)
                    }

                    if (ALLOW_TURN_OFF_ANYWAY) {
                        TextButton(
                            onClick = onTurnOffAnyway,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .semantics { contentDescription = turnOffLabel }
                        ) {
                            Text(
                                text = turnOffLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
