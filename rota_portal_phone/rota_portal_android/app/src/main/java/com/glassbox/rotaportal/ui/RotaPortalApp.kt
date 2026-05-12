package com.glassbox.rotaportal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassbox.rotaportal.shared.MonthlyRota
import com.glassbox.rotaportal.shared.OpsgenieOverride
import com.glassbox.rotaportal.shared.ScheduleEntry
import com.glassbox.rotaportal.shared.ShiftKind
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun RotaPortalApp(
    viewModel: RotaPortalViewModel,
    tokenDocsUrl: String,
) {
    val uiState by viewModel.uiState.collectAsState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (uiState.stage) {
                StartupStage.Checking,
                StartupStage.Validating -> LoadingScreen("Validating Opsgenie token")
                StartupStage.Setup -> SetupScreen(
                    message = uiState.message,
                    tokenDocsUrl = tokenDocsUrl,
                    onSubmit = viewModel::submitToken,
                )
                StartupStage.ValidationError -> ValidationErrorScreen(
                    message = uiState.message ?: "Validation failed.",
                    onRetry = viewModel::validateOnStartup,
                    onReset = viewModel::resetToken,
                )
                StartupStage.Authenticated -> RotaScreen(
                    uiState = uiState,
                    onRefresh = viewModel::refreshRota,
                    onToday = viewModel::goToToday,
                    onShiftMonth = viewModel::shiftMonth,
                    onSetViewMode = viewModel::setViewMode,
                    onSetBulkMode = viewModel::setBulkMode,
                    onToggleShiftSelection = viewModel::toggleShiftSelection,
                    onOpenSingleOverride = viewModel::openSingleOverride,
                    onOpenBulkOverride = viewModel::openBulkOverride,
                    onUpdateDialogMember = viewModel::updateDialogMember,
                    onUpdateDialogPartial = viewModel::updateDialogPartial,
                    onUpdateDialogStart = viewModel::updateDialogStart,
                    onUpdateDialogEnd = viewModel::updateDialogEnd,
                    onCloseOverrideDialog = viewModel::closeOverrideDialog,
                    onSubmitOverride = viewModel::submitOverride,
                    onConsumeTodayFocus = viewModel::consumeTodayFocus,
                    onResetToken = viewModel::resetToken,
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(message)
        }
    }
}

@Composable
private fun SetupScreen(
    message: String?,
    tokenDocsUrl: String,
    onSubmit: (String) -> Unit,
) {
    var token by rememberSaveable { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Opsgenie token setup",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Paste your API token to continue.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = message != null,
            supportingText = {
                if (message != null) {
                    Text(message)
                }
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tokenDocsUrl.isNotBlank()) {
                TextButton(onClick = { uriHandler.openUri(tokenDocsUrl) }) {
                    Text("Token documentation")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }
            Button(
                enabled = token.isNotBlank(),
                onClick = { onSubmit(token) },
            ) {
                Text("Validate token")
            }
        }
    }
}

@Composable
private fun ValidationErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Token validation unavailable",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(message)
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
            TextButton(onClick = onReset) {
                Text("Reset token")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RotaScreen(
    uiState: RotaPortalUiState,
    onRefresh: () -> Unit,
    onToday: () -> Unit,
    onShiftMonth: (Int) -> Unit,
    onSetViewMode: (RotaViewMode) -> Unit,
    onSetBulkMode: (Boolean) -> Unit,
    onToggleShiftSelection: (String) -> Unit,
    onOpenSingleOverride: (ScheduleEntry) -> Unit,
    onOpenBulkOverride: () -> Unit,
    onUpdateDialogMember: (String) -> Unit,
    onUpdateDialogPartial: (Boolean) -> Unit,
    onUpdateDialogStart: (String) -> Unit,
    onUpdateDialogEnd: (String) -> Unit,
    onCloseOverrideDialog: () -> Unit,
    onSubmitOverride: () -> Unit,
    onConsumeTodayFocus: () -> Unit,
    onResetToken: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rota Portal") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh rota")
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Reset token") },
                            leadingIcon = {
                                Icon(Icons.Default.Settings, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onResetToken()
                            },
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        RotaContent(
            uiState = uiState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            onToday = onToday,
            onShiftMonth = onShiftMonth,
            onSetViewMode = onSetViewMode,
            onSetBulkMode = onSetBulkMode,
            onToggleShiftSelection = onToggleShiftSelection,
            onOpenSingleOverride = onOpenSingleOverride,
            onOpenBulkOverride = onOpenBulkOverride,
            onConsumeTodayFocus = onConsumeTodayFocus,
        )
        uiState.overrideDialog?.let { dialog ->
            OverrideDialog(
                dialog = dialog,
                members = uiState.rota?.sortedMembers().orEmpty(),
                submitting = uiState.overrideSubmitting,
                onMemberChange = onUpdateDialogMember,
                onPartialChange = onUpdateDialogPartial,
                onStartChange = onUpdateDialogStart,
                onEndChange = onUpdateDialogEnd,
                onDismiss = onCloseOverrideDialog,
                onSubmit = onSubmitOverride,
            )
        }
    }
}

@Composable
private fun RotaContent(
    uiState: RotaPortalUiState,
    modifier: Modifier,
    onToday: () -> Unit,
    onShiftMonth: (Int) -> Unit,
    onSetViewMode: (RotaViewMode) -> Unit,
    onSetBulkMode: (Boolean) -> Unit,
    onToggleShiftSelection: (String) -> Unit,
    onOpenSingleOverride: (ScheduleEntry) -> Unit,
    onOpenBulkOverride: () -> Unit,
    onConsumeTodayFocus: () -> Unit,
) {
    val rota = uiState.rota
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    LaunchedEffect(rota?.year, rota?.month, uiState.viewMode, uiState.focusTodayAfterLoad) {
        if (rota != null && uiState.focusTodayAfterLoad) {
            val today = LocalDate.now()
            if (today.year == rota.year && today.monthValue == rota.month) {
                if (uiState.viewMode == RotaViewMode.Month) {
                    scrollGridToToday(gridState, rota, today)
                } else {
                    scrollListToToday(listState, rota, today)
                }
            }
            onConsumeTodayFocus()
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RotaHeader(uiState = uiState, rota = rota)
        RotaToolbar(
            rota = rota,
            viewMode = uiState.viewMode,
            bulkMode = uiState.bulkMode,
            selectedCount = uiState.selectedShiftIds.size,
            onToday = onToday,
            onShiftMonth = onShiftMonth,
            onSetViewMode = onSetViewMode,
            onSetBulkMode = onSetBulkMode,
            onOpenBulkOverride = onOpenBulkOverride,
        )
        uiState.statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if ("failed" in message.lowercase()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        if (uiState.rotaLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text("Loading rota")
            }
        } else if (rota != null) {
            when (uiState.viewMode) {
                RotaViewMode.Month -> MonthGrid(
                    rota = rota,
                    selectedShiftIds = uiState.selectedShiftIds,
                    bulkMode = uiState.bulkMode,
                    gridState = gridState,
                    onToggleShiftSelection = onToggleShiftSelection,
                    onOpenSingleOverride = onOpenSingleOverride,
                )
                RotaViewMode.List -> ShiftList(
                    rota = rota,
                    selectedShiftIds = uiState.selectedShiftIds,
                    bulkMode = uiState.bulkMode,
                    listState = listState,
                    onToggleShiftSelection = onToggleShiftSelection,
                    onOpenSingleOverride = onOpenSingleOverride,
                )
            }
        }
    }
}

@Composable
private fun RotaHeader(
    uiState: RotaPortalUiState,
    rota: MonthlyRota?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = rota?.let { "${it.scheduleName} / ${it.rotationName}" } ?: "Connected to Opsgenie",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = uiState.accountName?.let { "Account: $it" } ?: "Account validation succeeded.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (rota != null) {
            Text(
                text = "${monthName(rota.month)} ${rota.year}: ${rota.entries.size} shifts, ${rota.overrides.size} overrides",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        uiState.rotaError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RotaToolbar(
    rota: MonthlyRota?,
    viewMode: RotaViewMode,
    bulkMode: Boolean,
    selectedCount: Int,
    onToday: () -> Unit,
    onShiftMonth: (Int) -> Unit,
    onSetViewMode: (RotaViewMode) -> Unit,
    onSetBulkMode: (Boolean) -> Unit,
    onOpenBulkOverride: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onShiftMonth(-1) }) {
                Text("Prev")
            }
            Text(
                text = rota?.let { "${monthName(it.month)} ${it.year}" } ?: "",
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { onShiftMonth(1) }) {
                Text("Next")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onToday) {
                Text("Today")
            }
            ViewModeButton(
                label = "Month",
                selected = viewMode == RotaViewMode.Month,
                onClick = { onSetViewMode(RotaViewMode.Month) },
            )
            ViewModeButton(
                label = "List",
                selected = viewMode == RotaViewMode.List,
                onClick = { onSetViewMode(RotaViewMode.List) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = bulkMode,
                    onCheckedChange = onSetBulkMode,
                )
                Text("Bulk override")
            }
            if (bulkMode) {
                Button(
                    enabled = selectedCount > 0,
                    onClick = onOpenBulkOverride,
                ) {
                    Text(if (selectedCount == 0) "Override selected" else "Override selected ($selectedCount)")
                }
            }
        }
    }
}

@Composable
private fun ViewModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Text(label)
    }
}

@Composable
private fun MonthGrid(
    rota: MonthlyRota,
    selectedShiftIds: Set<String>,
    bulkMode: Boolean,
    gridState: LazyGridState,
    onToggleShiftSelection: (String) -> Unit,
    onOpenSingleOverride: (ScheduleEntry) -> Unit,
) {
    val days = remember(rota.year, rota.month) { monthDays(rota.year, rota.month) }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(days) { _, day ->
                DayCell(
                    day = day,
                    rota = rota,
                    selectedShiftIds = selectedShiftIds,
                    bulkMode = bulkMode,
                    onToggleShiftSelection = onToggleShiftSelection,
                    onOpenSingleOverride = onOpenSingleOverride,
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    rota: MonthlyRota,
    selectedShiftIds: Set<String>,
    bulkMode: Boolean,
    onToggleShiftSelection: (String) -> Unit,
    onOpenSingleOverride: (ScheduleEntry) -> Unit,
) {
    val entries = rota.entries.filter { it.overlaps(day.date) }
    val today = day.date == LocalDate.now()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp),
        border = if (today) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = when {
                today -> MaterialTheme.colorScheme.tertiaryContainer
                !day.inMonth -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (day.inMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            entries.take(3).forEach { entry ->
                ShiftChip(
                    entry = entry,
                    override = rota.overrideFor(entry),
                    selected = entry.id in selectedShiftIds,
                    bulkMode = bulkMode,
                    compact = true,
                    onClick = {
                        if (bulkMode) onToggleShiftSelection(entry.id) else onOpenSingleOverride(entry)
                    },
                )
            }
            if (entries.size > 3) {
                Text("+${entries.size - 3}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ShiftList(
    rota: MonthlyRota,
    selectedShiftIds: Set<String>,
    bulkMode: Boolean,
    listState: LazyListState,
    onToggleShiftSelection: (String) -> Unit,
    onOpenSingleOverride: (ScheduleEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(rota.entries, key = { it.id }) { entry ->
            ShiftRow(
                entry = entry,
                rota = rota,
                selected = entry.id in selectedShiftIds,
                bulkMode = bulkMode,
                onClick = {
                    if (bulkMode) onToggleShiftSelection(entry.id) else onOpenSingleOverride(entry)
                },
            )
        }
    }
}

@Composable
private fun ShiftRow(
    entry: ScheduleEntry,
    rota: MonthlyRota,
    selected: Boolean,
    bulkMode: Boolean,
    onClick: () -> Unit,
) {
    val override = rota.overrides.firstOrNull { it.start == entry.start && it.end == entry.end }
    val effectiveMember = override?.member ?: entry.applyMember
    val changed = effectiveMember != entry.member

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else if (entry.kind == ShiftKind.Weekend) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        if (bulkMode) append(if (selected) "[x] " else "[ ] ")
                        append(if (changed) "${entry.member} -> $effectiveMember" else effectiveMember)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = entry.kind.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(
                            color = if (entry.kind == ShiftKind.Weekend) Color(0xFFB45309) else Color(0xFF0F766E),
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                )
            }
            Text(
                text = "${formatDate(entry.start)} ${formatTime(entry.start)} - ${formatDate(entry.end)} ${formatTime(entry.end)} UTC",
                style = MaterialTheme.typography.bodyMedium,
            )
            override?.let {
                Text(
                    text = "Opsgenie override: ${it.member}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ShiftChip(
    entry: ScheduleEntry,
    override: OpsgenieOverride?,
    selected: Boolean,
    bulkMode: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val effectiveMember = override?.member ?: entry.applyMember
    val label = buildString {
        if (bulkMode) append(if (selected) "x " else "- ")
        append(effectiveMember)
    }
    val color = when {
        selected -> MaterialTheme.colorScheme.primary
        entry.kind == ShiftKind.Weekend -> Color(0xFFB45309)
        override != null -> Color(0xFF7C3AED)
        else -> Color(0xFF0F766E)
    }
    Text(
        text = if (compact) label.take(12) else label,
        modifier = Modifier
            .fillMaxWidth()
            .background(color = color, shape = MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = if (label.length > 5) 9.sp else 10.sp,
            lineHeight = 10.sp,
        ),
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun OverrideDialog(
    dialog: OverrideDialogState,
    members: List<String>,
    submitting: Boolean,
    onMemberChange: (String) -> Unit,
    onPartialChange: (Boolean) -> Unit,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (dialog) {
                    is OverrideDialogState.Single -> "Override shift"
                    is OverrideDialogState.Bulk -> "Override ${dialog.entries.size} shifts"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(dialog.summaryText(), style = MaterialTheme.typography.bodyMedium)
                MemberSelector(
                    selectedMember = dialog.selectedMember,
                    members = members,
                    onMemberChange = onMemberChange,
                )
                if (dialog is OverrideDialogState.Single) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !dialog.partial,
                            onClick = { onPartialChange(false) },
                        )
                        Text("Full shift")
                        Spacer(modifier = Modifier.width(12.dp))
                        RadioButton(
                            selected = dialog.partial,
                            onClick = { onPartialChange(true) },
                        )
                        Text("Partial")
                    }
                    if (dialog.partial) {
                        OutlinedTextField(
                            value = dialog.start,
                            onValueChange = onStartChange,
                            label = { Text("Start UTC") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = dialog.end,
                            onValueChange = onEndChange,
                            label = { Text("End UTC") },
                            singleLine = true,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !submitting,
                onClick = onSubmit,
            ) {
                Text(if (submitting) "Saving" else "Override")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun MemberSelector(
    selectedMember: String,
    members: List<String>,
    onMemberChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }) {
            Text(selectedMember)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            members.forEach { member ->
                DropdownMenuItem(
                    text = { Text(member) },
                    onClick = {
                        expanded = false
                        onMemberChange(member)
                    },
                )
            }
        }
    }
}

private suspend fun scrollGridToToday(
    gridState: LazyGridState,
    rota: MonthlyRota,
    today: LocalDate,
) {
    val index = monthDays(rota.year, rota.month).indexOfFirst { it.date == today }
    if (index >= 0) {
        gridState.animateScrollToItem(index.coerceAtLeast(0))
    }
}

private suspend fun scrollListToToday(
    listState: LazyListState,
    rota: MonthlyRota,
    today: LocalDate,
) {
    val index = rota.entries.indexOfFirst { it.overlaps(today) }
    if (index >= 0) {
        listState.animateScrollToItem(index)
    }
}

private data class CalendarDay(
    val date: LocalDate,
    val inMonth: Boolean,
)

private fun monthDays(year: Int, month: Int): List<CalendarDay> {
    val target = YearMonth.of(year, month)
    val first = target.atDay(1)
    val daysBackToSunday = first.dayOfWeek.value % 7
    val gridStart = first.minusDays(daysBackToSunday.toLong())
    return (0 until 42).map { offset ->
        val date = gridStart.plusDays(offset.toLong())
        CalendarDay(date = date, inMonth = date.monthValue == month)
    }
}

private fun MonthlyRota.sortedMembers(): List<String> {
    return teamMembers.sortedWith { left, right ->
        val leftIndex = defaultAssignableMembers.indexOf(left).takeIf { it >= 0 } ?: 99
        val rightIndex = defaultAssignableMembers.indexOf(right).takeIf { it >= 0 } ?: 99
        if (leftIndex != rightIndex) leftIndex - rightIndex else left.compareTo(right)
    }
}

private fun MonthlyRota.overrideFor(entry: ScheduleEntry): OpsgenieOverride? {
    return overrides.firstOrNull { it.start == entry.start && it.end == entry.end }
}

private fun ScheduleEntry.overlaps(date: LocalDate): Boolean {
    val startDate = LocalDate.parse(start.substring(0, 10))
    val endDate = LocalDate.parse(end.substring(0, 10))
    return date >= startDate && date <= endDate
}

private fun OverrideDialogState.summaryText(): String {
    return when (this) {
        is OverrideDialogState.Single -> "${entry.member} ${entry.kind.name.lowercase()} shift\n${entry.start} to ${entry.end}"
        is OverrideDialogState.Bulk -> entries.take(4)
            .joinToString("\n") { "${it.member}: ${it.start} to ${it.end}" }
            .let { text -> if (entries.size > 4) "$text\n...and ${entries.size - 4} more" else text }
    }
}

private fun formatDate(value: String): String = value.substring(0, 10)

private fun formatTime(value: String): String = value.substring(11, 16)

private fun monthName(month: Int): String {
    return listOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December",
    )[month - 1]
}
