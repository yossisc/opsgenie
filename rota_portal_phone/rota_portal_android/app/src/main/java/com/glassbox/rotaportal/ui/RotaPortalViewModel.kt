package com.glassbox.rotaportal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glassbox.rotaportal.shared.BulkOverrideResult
import com.glassbox.rotaportal.shared.MonthlyRota
import com.glassbox.rotaportal.shared.OpsgenieClient
import com.glassbox.rotaportal.shared.RotaRepository
import com.glassbox.rotaportal.shared.ScheduleEntry
import com.glassbox.rotaportal.shared.TokenValidationResult
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RotaPortalViewModel(
    private val opsgenieClient: OpsgenieClient,
    private val rotaRepository: RotaRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RotaPortalUiState(stage = StartupStage.Checking))
    val uiState: StateFlow<RotaPortalUiState> = _uiState.asStateFlow()

    init {
        validateOnStartup()
    }

    fun validateOnStartup() {
        _uiState.update { it.copy(stage = StartupStage.Checking, message = null) }
        viewModelScope.launch {
            when (val result = opsgenieClient.validateStoredToken()) {
                is TokenValidationResult.Valid -> onAuthenticated(result.accountName)
                TokenValidationResult.Missing -> _uiState.value = RotaPortalUiState(
                    stage = StartupStage.Setup,
                )
                is TokenValidationResult.Invalid -> _uiState.value = RotaPortalUiState(
                    stage = StartupStage.Setup,
                    message = result.message,
                )
                is TokenValidationResult.Error -> _uiState.value = RotaPortalUiState(
                    stage = StartupStage.ValidationError,
                    message = result.message,
                )
            }
        }
    }

    fun submitToken(token: String) {
        _uiState.update { it.copy(stage = StartupStage.Validating, message = null) }
        viewModelScope.launch {
            when (val result = opsgenieClient.saveAndValidateToken(token)) {
                is TokenValidationResult.Valid -> onAuthenticated(result.accountName)
                TokenValidationResult.Missing -> _uiState.value = RotaPortalUiState(
                    stage = StartupStage.Setup,
                )
                is TokenValidationResult.Invalid -> _uiState.value = RotaPortalUiState(
                    stage = StartupStage.Setup,
                    message = result.message,
                )
                is TokenValidationResult.Error -> _uiState.value = RotaPortalUiState(
                    stage = StartupStage.Setup,
                    message = result.message,
                )
            }
        }
    }

    fun resetToken() {
        opsgenieClient.clearToken()
        _uiState.value = RotaPortalUiState(
            stage = StartupStage.Setup,
            message = "Token cleared.",
        )
    }

    fun refreshRota() {
        val current = _uiState.value
        val rota = current.rota
        if (rota == null) {
            val today = LocalDate.now()
            loadRota(today.year, today.monthValue, focusToday = true)
        } else {
            loadRota(rota.year, rota.month, focusToday = false)
        }
    }

    fun goToToday() {
        val today = LocalDate.now()
        loadRota(today.year, today.monthValue, focusToday = true)
    }

    fun shiftMonth(delta: Int) {
        val current = _uiState.value.rota
        val base = if (current != null) {
            LocalDate.of(current.year, current.month, 1)
        } else {
            LocalDate.now().withDayOfMonth(1)
        }
        val target = base.plusMonths(delta.toLong())
        loadRota(target.year, target.monthValue, focusToday = false)
    }

    fun setViewMode(viewMode: RotaViewMode) {
        _uiState.update { it.copy(viewMode = viewMode) }
    }

    fun setBulkMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                bulkMode = enabled,
                selectedShiftIds = if (enabled) it.selectedShiftIds else emptySet(),
            )
        }
    }

    fun toggleShiftSelection(entryId: String) {
        _uiState.update { state ->
            val selected = state.selectedShiftIds.toMutableSet()
            if (!selected.add(entryId)) {
                selected.remove(entryId)
            }
            state.copy(selectedShiftIds = selected)
        }
    }

    fun openSingleOverride(entry: ScheduleEntry) {
        _uiState.update {
            it.copy(
                overrideDialog = OverrideDialogState.Single(
                    entry = entry,
                    selectedMember = entry.applyMember,
                    partial = false,
                    start = entry.start,
                    end = entry.end,
                ),
                statusMessage = null,
            )
        }
    }

    fun openBulkOverride() {
        val state = _uiState.value
        val rota = state.rota ?: return
        val selectedEntries = rota.entries.filter { it.id in state.selectedShiftIds }
        if (selectedEntries.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Select at least one shift.") }
            return
        }
        _uiState.update {
            it.copy(
                overrideDialog = OverrideDialogState.Bulk(
                    entries = selectedEntries,
                    selectedMember = selectedEntries.first().applyMember,
                ),
                statusMessage = null,
            )
        }
    }

    fun updateDialogMember(member: String) {
        _uiState.update { state ->
            state.copy(
                overrideDialog = when (val dialog = state.overrideDialog) {
                    is OverrideDialogState.Single -> dialog.copy(selectedMember = member)
                    is OverrideDialogState.Bulk -> dialog.copy(selectedMember = member)
                    null -> null
                },
            )
        }
    }

    fun updateDialogPartial(partial: Boolean) {
        _uiState.update { state ->
            state.copy(
                overrideDialog = when (val dialog = state.overrideDialog) {
                    is OverrideDialogState.Single -> dialog.copy(partial = partial)
                    is OverrideDialogState.Bulk,
                    null -> dialog
                },
            )
        }
    }

    fun updateDialogStart(start: String) {
        _uiState.update { state ->
            state.copy(
                overrideDialog = when (val dialog = state.overrideDialog) {
                    is OverrideDialogState.Single -> dialog.copy(start = start)
                    is OverrideDialogState.Bulk,
                    null -> dialog
                },
            )
        }
    }

    fun updateDialogEnd(end: String) {
        _uiState.update { state ->
            state.copy(
                overrideDialog = when (val dialog = state.overrideDialog) {
                    is OverrideDialogState.Single -> dialog.copy(end = end)
                    is OverrideDialogState.Bulk,
                    null -> dialog
                },
            )
        }
    }

    fun closeOverrideDialog() {
        _uiState.update { it.copy(overrideDialog = null) }
    }

    fun submitOverride() {
        when (val dialog = _uiState.value.overrideDialog) {
            is OverrideDialogState.Single -> submitSingleOverride(dialog)
            is OverrideDialogState.Bulk -> submitBulkOverride(dialog)
            null -> return
        }
    }

    private fun onAuthenticated(accountName: String?) {
        _uiState.value = RotaPortalUiState(
            stage = StartupStage.Authenticated,
            accountName = accountName,
            rotaLoading = true,
        )
        goToToday()
    }

    private fun loadRota(year: Int, month: Int, focusToday: Boolean) {
        _uiState.update {
            it.copy(
                stage = StartupStage.Authenticated,
                rotaLoading = true,
                rotaError = null,
                statusMessage = null,
                focusTodayAfterLoad = focusToday,
            )
        }
        viewModelScope.launch {
            try {
                val rota = rotaRepository.loadMonth(year, month)
                _uiState.update {
                    it.copy(
                        rota = rota,
                        rotaLoading = false,
                        rotaError = rota.overridesError,
                        selectedShiftIds = emptySet(),
                        bulkMode = false,
                    )
                }
            } catch (exc: RuntimeException) {
                _uiState.update {
                    it.copy(
                        rotaLoading = false,
                        rotaError = "Failed to load rota.",
                    )
                }
            }
        }
    }

    fun consumeTodayFocus() {
        _uiState.update { it.copy(focusTodayAfterLoad = false) }
    }

    private fun submitSingleOverride(dialog: OverrideDialogState.Single) {
        _uiState.update { it.copy(overrideSubmitting = true, statusMessage = null) }
        viewModelScope.launch {
            try {
                val result = rotaRepository.overrideShift(
                    member = dialog.selectedMember,
                    start = if (dialog.partial) dialog.start else dialog.entry.start,
                    end = if (dialog.partial) dialog.end else dialog.entry.end,
                    partial = dialog.partial,
                )
                _uiState.update { state ->
                    val refreshed = state.rota?.withOverride(result.override)
                    state.copy(
                        rota = refreshed,
                        overrideDialog = null,
                        overrideSubmitting = false,
                        statusMessage = "Override ${result.operation.name.lowercase()}: ${result.alias}",
                    )
                }
            } catch (exc: Exception) {
                _uiState.update {
                    it.copy(
                        overrideSubmitting = false,
                        statusMessage = exc.message ?: "Override failed.",
                    )
                }
            }
        }
    }

    private fun submitBulkOverride(dialog: OverrideDialogState.Bulk) {
        _uiState.update { it.copy(overrideSubmitting = true, statusMessage = null) }
        viewModelScope.launch {
            try {
                val result: BulkOverrideResult = rotaRepository.overrideShifts(
                    member = dialog.selectedMember,
                    shifts = dialog.entries,
                )
                _uiState.update { state ->
                    val refreshed = result.items.fold(state.rota) { rota, item ->
                        val override = item.result?.override
                        if (rota != null && override != null) rota.withOverride(override) else rota
                    }
                    state.copy(
                        rota = refreshed,
                        overrideDialog = null,
                        overrideSubmitting = false,
                        bulkMode = false,
                        selectedShiftIds = emptySet(),
                        statusMessage = "Bulk override finished: ${result.succeeded} succeeded, ${result.failed} failed.",
                    )
                }
            } catch (exc: Exception) {
                _uiState.update {
                    it.copy(
                        overrideSubmitting = false,
                        statusMessage = exc.message ?: "Bulk override failed.",
                    )
                }
            }
        }
    }

    private fun MonthlyRota.withOverride(override: com.glassbox.rotaportal.shared.OpsgenieOverride): MonthlyRota {
        return copy(
            overrides = overrides
                .filterNot { it.start == override.start && it.end == override.end }
                .plus(override),
        )
    }
}

class RotaPortalViewModelFactory(
    private val opsgenieClient: OpsgenieClient,
    private val rotaRepository: RotaRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(RotaPortalViewModel::class.java))
        return RotaPortalViewModel(opsgenieClient, rotaRepository) as T
    }
}

data class RotaPortalUiState(
    val stage: StartupStage,
    val message: String? = null,
    val accountName: String? = null,
    val rota: MonthlyRota? = null,
    val rotaLoading: Boolean = false,
    val rotaError: String? = null,
    val viewMode: RotaViewMode = RotaViewMode.Month,
    val bulkMode: Boolean = false,
    val selectedShiftIds: Set<String> = emptySet(),
    val overrideDialog: OverrideDialogState? = null,
    val overrideSubmitting: Boolean = false,
    val statusMessage: String? = null,
    val focusTodayAfterLoad: Boolean = false,
)

enum class StartupStage {
    Checking,
    Setup,
    Validating,
    ValidationError,
    Authenticated,
}

enum class RotaViewMode {
    Month,
    List,
}

sealed interface OverrideDialogState {
    val selectedMember: String

    data class Single(
        val entry: ScheduleEntry,
        override val selectedMember: String,
        val partial: Boolean,
        val start: String,
        val end: String,
    ) : OverrideDialogState

    data class Bulk(
        val entries: List<ScheduleEntry>,
        override val selectedMember: String,
    ) : OverrideDialogState
}
