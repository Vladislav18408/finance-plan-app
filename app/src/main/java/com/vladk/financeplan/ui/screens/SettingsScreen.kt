package com.vladk.financeplan.ui.screens

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(data: AppData, vm: FinanceViewModel, notify: (String) -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(vm.exportJson().toByteArray()) }
            }.onSuccess { notify(s.exported) }.onFailure { notify(it.message ?: "Error") }
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

        Text(
            "Finance Plan 1.0",
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
            onConfirm = { notify(if (vm.importJson(text)) s.imported else s.importFailed) },
            onDismiss = { pendingImport = null },
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
