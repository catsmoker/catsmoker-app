package com.catsmoker.app.screens.gaming.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catsmoker.app.platform.device.isSamsung
import com.catsmoker.app.platform.display.AttachedDisplay
import com.catsmoker.app.platform.display.DisplayRateMath
import com.catsmoker.app.platform.display.FramePacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen state for the refresh-rate card.
 *
 * @param selectedDisplayId the display the card acts on. Follows the external display when one
 *   is attached, because that is the one whose rate a user cannot set anywhere else, and falls
 *   back to the built-in panel otherwise.
 * @param savedHz the preference this device would apply for the *selected* display's context.
 * @param savedScope which context that preference belongs to, so the label can name it.
 */
data class DisplayUiState(
    val engine: DisplayEngineState = DisplayEngineState(),
    val selectedDisplayId: Int? = null,
    val busy: Boolean = false,
    val savedHz: Float? = null,
    val savedScope: String = DisplayRateStore.GLOBAL
) {
    val selected: AttachedDisplay?
        get() = engine.displays.firstOrNull { it.id == selectedDisplayId } ?: engine.external ?: engine.builtIn

    val hasExternal: Boolean get() = engine.external != null

    /** True while a mode switch is waiting for the user to keep it. */
    val awaitingConfirmation: Boolean get() = engine.revertSeconds != null
}

/**
 * The refresh-rate card's ViewModel.
 *
 * It owns no display logic of its own — [DisplayEngine] does the work and holds the state, and
 * this class only decides which display the card is looking at and which context a saved
 * preference belongs to. Kept apart from Gaming Tools' main ViewModel so the display slice stays
 * one vertical: screen, state and engine behind one card.
 */
@HiltViewModel
class DisplayViewModel @Inject constructor(
    private val engine: DisplayEngine,
    private val rates: DisplayRateStore
) : ViewModel() {

    private val _ui = MutableStateFlow(DisplayUiState())
    val ui: StateFlow<DisplayUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            // A change left unconfirmed by a previous process is rolled back before anything
            // is shown, so the card never presents a state nobody agreed to.
            engine.recoverIfNeeded()
            engine.refresh()
            syncSelection()
        }
        viewModelScope.launch {
            engine.state.collect { state ->
                _ui.update { current ->
                    current.copy(engine = state).let { next ->
                        if (next.selectedDisplayId == null || state.displays.none { it.id == next.selectedDisplayId }) {
                            next.copy(selectedDisplayId = (state.external ?: state.builtIn)?.id)
                        } else {
                            next
                        }
                    }.withSavedRate()
                }
            }
        }
    }

    fun refresh() = viewModelScope.launch { engine.refresh() }

    fun select(displayId: Int) = _ui.update {
        it.copy(selectedDisplayId = displayId).withSavedRate()
    }

    /** Applies [hz] to the selected display. Interactive, so a mode switch gets its countdown. */
    fun apply(hz: Float) {
        val target = _ui.value.selected ?: return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            engine.apply(target.id, hz, interactive = true)
            engine.refresh()
            _ui.update { it.copy(busy = false) }
        }
    }

    fun restore() {
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            engine.restore()
            engine.refresh()
            _ui.update { it.copy(busy = false) }
        }
    }

    fun confirm() = viewModelScope.launch { engine.confirm() }

    /** Keeps [hz] as this context's preference so the automation can apply it later. */
    fun savePreference(hz: Float) {
        val scope = scopeFor()
        rates.setRate(scope, hz)
        _ui.update { it.copy(savedHz = hz, savedScope = scope) }
    }

    private fun syncSelection() {
        _ui.update { it.copy(selectedDisplayId = (it.engine.external ?: it.engine.builtIn)?.id).withSavedRate() }
    }

    private fun scopeFor(): String {
        val state = _ui.value
        val selected = state.selected
        return when {
            selected != null && selected.isExternal && isSamsung() -> DisplayRateStore.DEX
            selected != null && selected.isExternal -> DisplayRateStore.EXTERNAL
            else -> DisplayRateStore.GLOBAL
        }
    }

    private fun DisplayUiState.withSavedRate(): DisplayUiState {
        val scope = when {
            selected?.isExternal == true && isSamsung() -> DisplayRateStore.DEX
            selected?.isExternal == true -> DisplayRateStore.EXTERNAL
            else -> DisplayRateStore.GLOBAL
        }
        return copy(savedHz = rates.rate(scope), savedScope = scope)
    }

    /**
     * Whether [hz] divides the selected display's current rate evenly, or null when the
     * question does not apply.
     *
     * Only answered for an **external** display. On a panel CatSmoker can switch modes, the
     * panel's rate becomes the chosen rate, so the frame always lands on one refresh and an
     * "uneven" badge would be a false warning — a 90 Hz chip on a 120 Hz panel read
     * "held unevenly" purely because the panel had not switched yet. On a monitor the signal
     * rate stays where the cable put it while applications are paced separately, and there
     * the mismatch is real information.
     */
    fun pacingOf(hz: Float): FramePacing? {
        val display = _ui.value.selected ?: return null
        if (!display.isExternal) return null
        val panel = display.currentHz ?: return null
        return DisplayRateMath.pacing(panel, hz)
    }
}
