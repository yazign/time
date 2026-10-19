package com.mystopwatch.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel(this)
        val storage = Storage(this)
        setContent { AppTheme { App(storage) } }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(storage: Storage) {
    val context = LocalContext.current
    var stopwatches by remember { mutableStateOf(storage.loadStopwatches()) }
    var history by remember { mutableStateOf(storage.loadHistory()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Stopwatch?>(null) }
    var deleteTarget by remember { mutableStateOf<Stopwatch?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    fun saveStopwatches(list: List<Stopwatch>) {
        stopwatches = list
        now = System.currentTimeMillis()
        storage.saveStopwatches(list)
        updateNotifications(context, list)
    }

    fun replace(s: Stopwatch) {
        saveStopwatches(stopwatches.map { if (it.id == s.id) s else it })
    }

    fun saveHistory(list: List<Record>) {
        history = list
        storage.saveHistory(list)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateNotifications(context, stopwatches) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        updateNotifications(context, stopwatches)
    }

    // Обновляем экран, пока хоть один секундомер идёт
    val anyRunning = stopwatches.any { it.running }
    LaunchedEffect(anyRunning) {
        while (anyRunning) {
            now = System.currentTimeMillis()
            delay(30)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Секундомер") })
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Секундомеры") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("История (${history.size})") })
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(onClick = { showAdd = true }) { Text("+ Добавить") }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (tab == 0) {
                if (stopwatches.isEmpty()) {
                    Text(
                        "Нажмите «Добавить», чтобы создать секундомер",
                        Modifier.align(Alignment.Center).padding(32.dp),
                        textAlign = TextAlign.Center
                    )
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(stopwatches, key = { it.id }) { s ->
                        StopwatchCard(
                            s = s,
                            now = now,
                            onToggle = {
                                val t = System.currentTimeMillis()
                                replace(
                                    if (s.running) s.copy(accumulated = s.elapsed(t), startedAt = null)
                                    else s.copy(startedAt = t)
                                )
                            },
                            onSave = {
                                val t = System.currentTimeMillis()
                                val d = s.elapsed(t)
                                if (d > 0) {
                                    saveHistory(listOf(Record(s.name, t, d)) + history)
                                    Toast.makeText(context, "Сохранено в историю", Toast.LENGTH_SHORT).show()
                                }
                                replace(s.copy(accumulated = 0L, startedAt = null))
                            },
                            onReset = { replace(s.copy(accumulated = 0L, startedAt = null)) },
                            onRename = { renameTarget = s },
                            onDelete = { deleteTarget = s }
                        )
                    }
                }
            } else {
                HistoryScreen(
                    history = history,
                    onShareAll = {
                        shareText(context, "Замеры:\n" + history.joinToString("\n") { recordLine(it) })
                    },
                    onShareOne = { shareText(context, recordLine(it)) },
                    onClear = { confirmClear = true }
                )
            }
        }
    }

    if (showAdd) {
        NameDialog(
            title = "Новый секундомер",
            initial = "Секундомер ${stopwatches.size + 1}",
            onDismiss = { showAdd = false }
        ) { name ->
            val id = (stopwatches.maxOfOrNull { it.id } ?: 0L) + 1
            saveStopwatches(stopwatches + Stopwatch(id, name))
            showAdd = false
        }
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "Переименовать",
            initial = target.name,
            onDismiss = { renameTarget = null }
        ) { name ->
            stopwatches.find { it.id == target.id }?.let { replace(it.copy(name = name)) }
            renameTarget = null
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            text = "Удалить «${target.name}»?",
            onDismiss = { deleteTarget = null }
        ) {
            saveStopwatches(stopwatches.filter { it.id != target.id })
            deleteTarget = null
        }
    }

    if (confirmClear) {
        ConfirmDialog(text = "Очистить всю историю?", onDismiss = { confirmClear = false }) {
            saveHistory(emptyList())
            confirmClear = false
        }
    }
}

@Composable
fun StopwatchCard(
    s: Stopwatch,
    now: Long,
    onToggle: () -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onRename) { Text("Имя") }
                TextButton(onClick = onDelete) { Text("Удалить") }
            }
            Text(
                formatTime(s.elapsed(now)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                fontSize = 44.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )
            Button(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                Text(if (s.running) "Пауза" else "Старт")
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Сохранить") }
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Сброс") }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    history: List<Record>,
    onShareAll: () -> Unit,
    onShareOne: (Record) -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onShareAll, enabled = history.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("Поделиться")
            }
            OutlinedButton(onClick = onClear, enabled = history.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("Очистить")
            }
        }
        if (history.isEmpty()) {
            Text(
                "Здесь появятся сохранённые замеры.\nНажмите «Сохранить» на секундомере.",
                Modifier.fillMaxWidth().padding(32.dp),
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                "Нажмите на замер, чтобы отправить только его",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(history) { r ->
                ListItem(
                    headlineContent = { Text(r.name) },
                    supportingContent = { Text(formatDate(r.date)) },
                    trailingContent = {
                        Text(
                            formatTime(r.duration),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    modifier = Modifier.clickable { onShareOne(r) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Название") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim().ifEmpty { "Секундомер" }) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun ConfirmDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Да") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
