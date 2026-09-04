package ni.fsn.timestudy.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ni.fsn.timestudy.model.OperatorStudy

@Composable
fun TimeStudyApp(vm: StudyViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importWorkbook(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> if (uri != null) vm.export(uri) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state.screen) {
            AppScreen.Home -> HomeScreen(
                loading = state.loading,
                onImport = { importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }
            )
            AppScreen.Study -> StudyScreen(
                state = state,
                vm = vm,
                onExport = { exportLauncher.launch(state.exportReadyName) }
            )
        }
        state.error?.let {
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = vm::clearError) { Text("Cerrar") } }
            ) { Text(it) }
        }
        if (state.loading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text("Procesando Excel…")
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(loading: Boolean, onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Timer, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(22.dp))
        Text("Time Study Mobile", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Cronometra directamente en el teléfono y devuelve el mismo Excel con t1–t5 llenos.",
            modifier = Modifier.padding(top = 10.dp), textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onImport,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(10.dp)); Text("Importar estudio Excel", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudOff, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp)); Text("Funciona sin internet durante el estudio", fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyScreen(state: UiState, vm: StudyViewModel, onExport: () -> Unit) {
    val doc = state.document ?: return
    val operators = doc.visibleOperators
    val current = operators.getOrNull(state.selectedIndex) ?: return
    var editOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Estudio de tiempo", fontWeight = FontWeight.Bold)
                        Text(doc.displayName, fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onExport) { Icon(Icons.Default.SaveAlt, "Exportar") }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = vm::previous, enabled = state.selectedIndex > 0, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ChevronLeft, null); Text("Anterior")
                    }
                    Text("${state.selectedIndex + 1} / ${operators.size}", modifier = Modifier.padding(horizontal = 14.dp), fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = vm::next, enabled = state.selectedIndex < operators.lastIndex, modifier = Modifier.weight(1f)) {
                        Text("Siguiente"); Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(2.dp))
            ProgressCard(doc.completedCount, operators.size)
            OperationCard(current, onEdit = { editOpen = true })
            TimesCard(current, onClear = { slot -> vm.setTime(slot, null) })
            StopwatchCard(state, vm)
            ManageCard(onAdd = vm::addOperatorAfterCurrent, onDelete = vm::deleteCurrent)
            Button(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("Terminar y exportar Excel") }
            Spacer(Modifier.height(18.dp))
        }
    }

    if (editOpen) OperatorEditDialog(current, onDismiss = { editOpen = false }) { code, name, ant, fsn, opTime ->
        vm.updateOperatorIdentity(code, name, ant, fsn, opTime); editOpen = false
    }
}

@Composable
private fun ProgressCard(done: Int, total: Int) {
    val p = if (total == 0) 0f else done.toFloat() / total
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Avance del estudio", fontWeight = FontWeight.SemiBold)
                Text("$done/$total completos", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp)); LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun OperationCard(op: OperatorStudy, onEdit: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Operación ${op.position}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(op.operation.ifBlank { "Sin operación" }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("# Ope ${op.operationCode ?: "—"}  •  ${op.machine.ifBlank { "Sin máquina" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar operario") }
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            InfoRow(Icons.Default.Badge, "Operario", op.operatorName.ifBlank { "Sin asignar" })
            InfoRow(Icons.Default.QrCode, "Código", op.employeeCode.ifBlank { "—" })
            InfoRow(Icons.Default.WorkHistory, "Antigüedad", op.seniority.ifBlank { "—" })
            InfoRow(Icons.Default.Factory, "Tiempo FSN", op.timeFsn.ifBlank { "—" })
            InfoRow(Icons.Default.Engineering, "En operación", op.timeOperation.ifBlank { "—" })
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, Modifier.width(108.dp).padding(start = 8.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TimesCard(op: OperatorStudy, onClear: (Int) -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cronometrajes", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${op.completedCycles}/5", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            op.times.forEachIndexed { i, t ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = if (t == null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("t${i + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
                        Text(if (t == null) "Pendiente" else String.format("%.2f s", t), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        if (t != null) IconButton(onClick = { onClear(i) }, Modifier.size(32.dp)) { Icon(Icons.Default.Refresh, "Repetir", Modifier.size(18.dp)) }
                    }
                }
            }
            op.averageSeconds?.let { Text("Promedio actual: ${String.format("%.2f", it)} s/pz", modifier = Modifier.padding(top = 10.dp), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun StopwatchCard(state: UiState, vm: StudyViewModel) {
    val seconds = state.elapsedMs / 1000
    val centis = (state.elapsedMs % 1000) / 10
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CRONÓMETRO", letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(String.format("%02d:%02d.%02d", seconds / 60, seconds % 60, centis), fontSize = 48.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp))
            if (!state.running) {
                Button(onClick = vm::startTimer, modifier = Modifier.fillMaxWidth().height(62.dp), shape = RoundedCornerShape(20.dp)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(28.dp)); Spacer(Modifier.width(8.dp)); Text("INICIAR", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = vm::cancelCycle, modifier = Modifier.weight(1f).height(58.dp), shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Default.Close, null); Spacer(Modifier.width(4.dp)); Text("Cancelar")
                    }
                    Button(onClick = vm::recordTimer, modifier = Modifier.weight(1.45f).height(58.dp), shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Default.Save, null); Spacer(Modifier.width(5.dp)); Text("GRABAR", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text("Al grabar, el tiempo entra en el primer t1–t5 disponible.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun ManageCard(onAdd: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("Operarios de esta operación", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Agregar operario aquí") }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Default.PersonRemove, null); Spacer(Modifier.width(8.dp)); Text("Eliminar este operario")
            }
        }
    }
}

@Composable
private fun OperatorEditDialog(
    op: OperatorStudy,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var code by remember(op.id) { mutableStateOf(op.employeeCode) }
    var name by remember(op.id) { mutableStateOf(op.operatorName) }
    var ant by remember(op.id) { mutableStateOf(op.seniority) }
    var fsn by remember(op.id) { mutableStateOf(op.timeFsn) }
    var opTime by remember(op.id) { mutableStateOf(op.timeOperation) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Datos del operario") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(code, { code = it }, label = { Text("Código") }, singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text("Operario") })
                OutlinedTextField(ant, { ant = it }, label = { Text("Antigüedad") }, singleLine = true)
                OutlinedTextField(fsn, { fsn = it }, label = { Text("Tiempo FSN") }, singleLine = true)
                OutlinedTextField(opTime, { opTime = it }, label = { Text("Tiempo en operación") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onSave(code, name, ant, fsn, opTime) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
