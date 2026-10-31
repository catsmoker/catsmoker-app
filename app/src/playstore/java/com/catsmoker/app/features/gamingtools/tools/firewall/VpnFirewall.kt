package com.catsmoker.app.features.gamingtools.tools.firewall

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play variant stub for the full build's local VPN network block.
 *
 * Google Play's VpnService policy requires the VPN to be core functionality.
 * A gaming optimizer does not qualify, so this variant declares no VpnService
 * and no `BIND_VPN_SERVICE` service at all. The real implementation lives in
 * `src/full` under the same fully qualified name; shared code compiles against
 * this stub and hides the VPN switch via
 * [com.catsmoker.app.system.VariantCapabilities.HAS_VPN_FIREWALL].
 *
 * The Data Saver half of network blocking remains fully available on Play.
 */
@Singleton
class VpnFirewall @Inject constructor() {

    data class State(
        val running: Boolean = false,
        val blockedCount: Int = 0,
        val lastError: String? = null
    )

    sealed interface Consent {
        data object Granted : Consent
        data class Required(val intent: android.content.Intent) : Consent
        data class Unknown(val reason: String) : Consent
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * No VPN can be established in this variant. `Unknown` is the honest answer:
     * the platform check was not performed because the feature is not compiled in.
     */
    fun consent(): Consent = Consent.Unknown("not_available")

    /** The shared app set is irrelevant here; nothing is routed. */
    fun blockTargets(gamePackages: List<String>): List<String> = emptyList()

    /** No service to start. The UI is hidden behind the variant capability flag. */
    fun start(gamePackages: List<String>): String? = null

    fun stop() = Unit

    fun systemReportsVpn(): Boolean? = null
}
