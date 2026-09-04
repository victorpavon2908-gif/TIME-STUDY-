package ni.fsn.timestudy.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ni.fsn.timestudy.data.ExcelStudyRepository
import ni.fsn.timestudy.model.OperatorStudy
import ni.fsn.timestudy.model.StudyDocument

sealed interface AppScreen {
    data object Home : AppScreen
    data object Study : AppScreen
}

data class UiState(
    val screen: AppScreen = AppScreen.Home,
    val loading: Boolean = false,
    val error: String? = null,
    val document: StudyDocument? = null,
    val selectedIndex: Int = 0,
    val running: Boolean = false,
    val elapsedMs: Long = 0L,
    val exportReadyName: String = "TimeStudy_Completado.xlsx"
)

class StudyViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ExcelStudyRepository(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var timerStartedAt = 0L

    fun importWorkbook(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.importWorkbook(uri) }
                .onSuccess { doc ->
                    val exportName = doc.displayName.substringBeforeLast('.', doc.displayName) + "_Completado.xlsx"
                    _state.value = _state.value.copy(
                        loading = false,
                        document = doc,
                        selectedIndex = 0,
                        screen = AppScreen.Study,
                        exportReadyName = exportName
                    )
                }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message ?: "Error al importar") }
        }
    }

    fun select(index: Int) {
        stopTimer(reset = true)
        val size = _state.value.document?.visibleOperators?.size ?: 0
        if (size > 0) _state.value = _state.value.copy(selectedIndex = index.coerceIn(0, size - 1))
    }

    fun next() = select(_state.value.selectedIndex + 1)
    fun previous() = select(_state.value.selectedIndex - 1)

    fun startTimer() {
        if (_state.value.running) return
        timerStartedAt = SystemClock.elapsedRealtime()
        _state.value = _state.value.copy(running = true, elapsedMs = 0L)
        timerJob = viewModelScope.launch {
            while (true) {
                _state.value = _state.value.copy(elapsedMs = SystemClock.elapsedRealtime() - timerStartedAt)
                delay(16)
            }
        }
    }

    fun recordTimer() {
        if (!_state.value.running) return
        val seconds = (SystemClock.elapsedRealtime() - timerStartedAt) / 1000.0
        val rounded = kotlin.math.round(seconds * 100.0) / 100.0
        stopTimer(reset = true)
        setNextEmptyTime(rounded)
    }

    fun cancelCycle() = stopTimer(reset = true)

    fun setTime(slot: Int, seconds: Double?) {
        updateSelected { op ->
            val times = op.times.toMutableList()
            if (slot in 0..4) times[slot] = seconds
            op.copy(times = times)
        }
    }

    private fun setNextEmptyTime(seconds: Double) {
        updateSelected { op ->
            val times = op.times.toMutableList()
            val slot = times.indexOfFirst { it == null }.let { if (it == -1) 4 else it }
            times[slot] = seconds
            op.copy(times = times)
        }
    }

    fun addOperatorAfterCurrent() {
        val doc = _state.value.document ?: return
        val visible = doc.visibleOperators
        if (visible.isEmpty()) return
        val current = visible[_state.value.selectedIndex]
        val all = doc.operators.toMutableList()
        val physicalIndex = all.indexOfFirst { it.id == current.id }
        val added = current.copy(
            id = java.util.UUID.randomUUID().toString(),
            originalRow = null,
            employeeCode = "",
            seniority = "",
            timeFsn = "",
            timeOperation = "",
            operatorName = "",
            times = List(5) { null },
            deleted = false
        )
        all.add(physicalIndex + 1, added)
        _state.value = _state.value.copy(document = doc.copy(operators = all), selectedIndex = _state.value.selectedIndex + 1)
    }

    fun deleteCurrent() {
        val doc = _state.value.document ?: return
        val visible = doc.visibleOperators
        if (visible.size <= 1) return
        val current = visible[_state.value.selectedIndex]
        val all = doc.operators.map { if (it.id == current.id) it.copy(deleted = true) else it }
        val nextIndex = _state.value.selectedIndex.coerceAtMost(visible.size - 2)
        _state.value = _state.value.copy(document = doc.copy(operators = all), selectedIndex = nextIndex)
    }

    fun updateOperatorIdentity(
        employeeCode: String,
        operatorName: String,
        seniority: String,
        timeFsn: String,
        timeOperation: String
    ) {
        updateSelected { it.copy(
            employeeCode = employeeCode,
            operatorName = operatorName,
            seniority = seniority,
            timeFsn = timeFsn,
            timeOperation = timeOperation
        ) }
    }

    private fun updateSelected(transform: (OperatorStudy) -> OperatorStudy) {
        val doc = _state.value.document ?: return
        val visible = doc.visibleOperators
        val current = visible.getOrNull(_state.value.selectedIndex) ?: return
        val all = doc.operators.map { if (it.id == current.id) transform(it) else it }
        _state.value = _state.value.copy(document = doc.copy(operators = all))
    }

    fun export(uri: Uri) {
        val doc = _state.value.document ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.exportWorkbook(doc, uri) }
                .onSuccess { _state.value = _state.value.copy(loading = false) }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message ?: "Error al exportar") }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    private fun stopTimer(reset: Boolean) {
        timerJob?.cancel(); timerJob = null
        _state.value = _state.value.copy(running = false, elapsedMs = if (reset) 0L else _state.value.elapsedMs)
    }
}
