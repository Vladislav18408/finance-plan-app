package com.vladk.financeplan.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vladk.financeplan.reminder.Reminders
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vladk.financeplan.data.AppData
import com.vladk.financeplan.ui.FinanceViewModel
import com.vladk.financeplan.ui.LocalStrings
import com.vladk.financeplan.ui.components.ConfirmDialog
import com.vladk.financeplan.ui.components.SectionCard
import com.vladk.financeplan.ui.theme.badColor
import java.time.LocalDate

private val currencies = listOf("€", "лв.", "$", "£")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(data: AppData, vm: FinanceViewModel, notify: (String) -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    // Daily reminder: whether Android will actually show it is re-checked whenever the screen comes back
    // (the user may just have changed it in the system settings).
    val reminder = data.settings.reminder
    var showTimePicker by remember { mutableStateOf(false) }
    var canNotify by remember { mutableStateOf(Reminders.canNotify(context)) }
    LifecycleResumeEffect(Unit) {
        canNotify = Reminders.canNotify(context)
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = Reminders.canNotify(context)
    }
    val openNotificationSettings: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(vm.exportJson().toByteArray()) }
            }.onSuccess { notify(s.exported) }.onFailure { notify(s.saveError) }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(vm.exportCsv(s).toByteArray()) }
            }.onSuccess { notify(s.csvSaved) }.onFailure { notify(s.saveError) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            }.onSuccess { pendingImport = it }.onFailure { notify(s.importFailed) }
        }
    }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = s.language) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("bg" to "Български", "en" to "English").forEachIndexed { i, (code, label) ->
                    SegmentedButton(
                        selected = data.settings.language == code,
                        onClick = { vm.setLanguage(code) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }
        }

        SectionCard(title = s.currency) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                currencies.forEach { c ->
                    FilterChip(
                        selected = data.settings.currency == c,
                        onClick = { vm.setCurrency(c) },
                        label = { Text(c) },
                    )
                }
            }
        }

        SectionCard(title = s.reminder) {
            Text(s.reminderHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .toggleable(value = reminder.enabled, role = Role.Switch) { on ->
                        vm.setReminder(reminder.copy(enabled = on))
                        // Android 13+ shows its own "allow notifications?" question the first time.
                        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canNotify) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.reminderDaily, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = reminder.enabled, onCheckedChange = null)
            }
            if (reminder.enabled) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showTimePicker = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.reminderTime, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        "%02d:%02d".format(reminder.hour, reminder.minute),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .toggleable(value = reminder.skipIfRecorded, role = Role.Checkbox) {
                            vm.setReminder(reminder.copy(skipIfRecorded = it))
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.reminderSkip, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
                    Checkbox(checked = reminder.skipIfRecorded, onCheckedChange = null)
                }
                if (!canNotify) {
                    Spacer(Modifier.height(4.dp))
                    Text(s.notificationsOff, style = MaterialTheme.typography.bodyMedium, color = badColor())
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = openNotificationSettings, modifier = Modifier.fillMaxWidth()) {
                        Text(s.allowNotifications)
                    }
                }
            }
        }

        SectionCard(title = s.backup) {
            Text(s.backupHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = { exportLauncher.launch("finance-backup-${LocalDate.now()}.json") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(s.exportBackup)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Upload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(s.importBackup)
            }
        }

        SectionCard(title = s.exportCsv) {
            Text(s.exportCsvHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { csvLauncher.launch("finance-expenses-${LocalDate.now()}.csv") },
                enabled = data.expenses.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.TableChart, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(s.exportCsv)
            }
        }

        SectionCard {
            OutlinedButton(
                onClick = { confirmDeleteAll = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = badColor()),
            ) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(s.deleteAll)
            }
        }

        val version = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        }
        Text(
            "Finance Plan $version",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    pendingImport?.let { text ->
        ConfirmDialog(
            title = s.importBackup,
            text = s.importConfirm,
            confirmLabel = s.confirm,
            onConfirm = {
                notify(
                    when (val skipped = vm.importJson(text)) {
                        null -> s.importFailed
                        0 -> s.imported
                        else -> s.importedSkipped.format(skipped)
                    },
                )
            },
            onDismiss = { pendingImport = null },
        )
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = reminder.hour, initialMinute = reminder.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(s.reminderTime) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setReminder(reminder.copy(hour = state.hour, minute = state.minute))
                    showTimePicker = false
                }) { Text(s.ok) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(s.cancel) } },
        )
    }

    if (confirmDeleteAll) {
        ConfirmDialog(
            title = s.deleteConfirmTitle,
            text = s.deleteAllConfirm,
            confirmLabel = s.delete,
            onConfirm = { vm.deleteAll(); notify(s.deletedAll) },
            onDismiss = { confirmDeleteAll = false },
        )
    }
}
