package ni.fsn.timestudy.model

import java.util.UUID

data class OperatorStudy(
    val id: String = UUID.randomUUID().toString(),
    val originalRow: Int? = null,
    val position: Int,
    val operationCode: Int?,
    val operation: String,
    val machine: String,
    val employeeCode: String,
    val seniority: String,
    val timeFsn: String,
    val timeOperation: String,
    val operatorName: String,
    val times: List<Double?> = List(5) { null },
    val deleted: Boolean = false
) {
    val completedCycles: Int get() = times.count { it != null }
    val isComplete: Boolean get() = completedCycles == 5
    val averageSeconds: Double?
        get() = times.filterNotNull().takeIf { it.isNotEmpty() }?.average()
}

data class StudyDocument(
    val displayName: String,
    val sheetName: String,
    val headerRow: Int,
    val dataStartRow: Int,
    val operators: List<OperatorStudy>,
    val lineName: String = "",
    val styleName: String = ""
) {
    val visibleOperators: List<OperatorStudy> get() = operators.filterNot { it.deleted }
    val completedCount: Int get() = visibleOperators.count { it.isComplete }
}
