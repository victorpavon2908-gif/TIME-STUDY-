package ni.fsn.timestudy.data

import android.content.Context
import android.net.Uri
import ni.fsn.timestudy.model.OperatorStudy
import ni.fsn.timestudy.model.StudyDocument
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

class ExcelStudyRepository(private val context: Context) {
    private var workingFile: File? = null

    fun importWorkbook(uri: Uri): StudyDocument {
        val name = queryName(uri) ?: "TimeStudy.xlsx"
        val local = File(context.filesDir, "active_${System.currentTimeMillis()}.xlsx")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "No se pudo abrir el documento" }
            FileOutputStream(local).use { output -> input.copyTo(output) }
        }
        workingFile = local

        FileInputStream(local).use { input ->
            WorkbookFactory.create(input).use { wb ->
                val sheet = wb.getSheet("Linea 11")
                    ?: wb.firstOrNull { s -> findHeaderRow(s) >= 0 }
                    ?: error("No encontré la tabla del estudio de tiempo")

                val headerRow = findHeaderRow(sheet)
                require(headerRow >= 0) { "No encontré las columnas t1-t5" }
                val dataStart = headerRow + 1
                val result = mutableListOf<OperatorStudy>()
                var emptyStreak = 0

                for (r in dataStart..sheet.lastRowNum) {
                    val row = sheet.getRow(r)
                    val pos = row?.numericOrNull(0)?.toInt()
                    val op = row?.text(2).orEmpty().trim()
                    val employee = row?.text(4).orEmpty().trim()
                    if (pos == null && op.isBlank() && employee.isBlank()) {
                        emptyStreak++
                        if (emptyStreak >= 4 && result.isNotEmpty()) break
                        continue
                    }
                    emptyStreak = 0
                    if (pos == null && op.isBlank()) continue

                    result += OperatorStudy(
                        originalRow = r,
                        position = pos ?: 0,
                        operationCode = row?.numericOrNull(1)?.toInt(),
                        operation = op,
                        machine = row?.text(3).orEmpty(),
                        employeeCode = employee,
                        seniority = row?.text(5).orEmpty(),
                        timeFsn = row?.text(6).orEmpty(),
                        timeOperation = row?.text(7).orEmpty(),
                        operatorName = row?.text(8).orEmpty(),
                        times = (9..13).map { c -> row?.numericOrNull(c) }
                    )
                }

                val line = sheet.getRow(6)?.text(2).orEmpty()
                val style = sheet.getRow(7)?.text(2).orEmpty()
                return StudyDocument(
                    displayName = name,
                    sheetName = sheet.sheetName,
                    headerRow = headerRow,
                    dataStartRow = dataStart,
                    operators = result,
                    lineName = line,
                    styleName = style
                )
            }
        }
    }

    /**
     * Exporta partiendo del XLSX original. Sólo se reescribe el bloque de detalle A:W.
     * Las demás hojas, imágenes, estilos y configuración del libro se conservan.
     */
    fun exportWorkbook(document: StudyDocument, outputUri: Uri) {
        val source = requireNotNull(workingFile) { "No hay un archivo activo" }
        FileInputStream(source).use { input ->
            WorkbookFactory.create(input).use { wb ->
                val sheet = wb.getSheet(document.sheetName)
                    ?: error("No existe la hoja ${document.sheetName}")

                val rows = document.visibleOperators
                val start = document.dataStartRow
                val oldLast = detectLastStudyRow(sheet, start)
                val targetLast = start + rows.lastIndex
                val clearTo = maxOf(oldLast, targetLast)

                // Conservamos una fila plantilla con formato antes de limpiar.
                val styleTemplate = sheet.getRow(start)

                for (r in start..clearTo) {
                    val row = sheet.getRow(r) ?: sheet.createRow(r)
                    for (c in 0..22) {
                        val cell = row.getCell(c) ?: row.createCell(c)
                        cell.setBlank()
                    }
                }

                rows.forEachIndexed { index, item ->
                    val r = start + index
                    val row = sheet.getRow(r) ?: sheet.createRow(r)
                    if (styleTemplate != null && r != start) cloneRowStyle(styleTemplate, row)
                    writeBaseFields(row, item)
                    writeTimeFields(row, item)
                    writeDetailFormulas(row, r + 1, rows, index)
                }

                // Limpia filas sobrantes de un estudio más largo previo.
                if (targetLast < oldLast) {
                    for (r in targetLast + 1..oldLast) {
                        val row = sheet.getRow(r) ?: continue
                        for (c in 0..22) (row.getCell(c) ?: row.createCell(c)).setBlank()
                    }
                }

                // Deja el cálculo para Excel al abrir el archivo.
                wb.creationHelper.createFormulaEvaluator().clearAllCachedResultValues()
                wb.setForceFormulaRecalculation(true)

                context.contentResolver.openOutputStream(outputUri, "w").use { output ->
                    requireNotNull(output) { "No se pudo crear el archivo" }
                    wb.write(output)
                }
            }
        }
    }

    private fun writeBaseFields(row: Row, item: OperatorStudy) {
        row.cell(0).setCellValue(item.position.toDouble())
        if (item.operationCode != null) row.cell(1).setCellValue(item.operationCode.toDouble()) else row.cell(1).setBlank()
        row.cell(2).setCellValue(item.operation)
        row.cell(3).setCellValue(item.machine)
        row.cell(4).setCellValue(item.employeeCode)
        row.cell(5).setCellValue(item.seniority)
        row.cell(6).setCellValue(item.timeFsn)
        row.cell(7).setCellValue(item.timeOperation)
        row.cell(8).setCellValue(item.operatorName)
    }

    private fun writeTimeFields(row: Row, item: OperatorStudy) {
        item.times.forEachIndexed { i, value ->
            val cell = row.cell(9 + i)
            if (value == null) cell.setBlank() else cell.setCellValue(value)
        }
    }

    private fun writeDetailFormulas(
        row: Row,
        excelRow: Int,
        all: List<OperatorStudy>,
        index: Int
    ) {
        row.cell(14).cellFormula = "IFERROR(AVERAGE(J$excelRow:N$excelRow),\"\")"
        row.cell(15).cellFormula = "IFERROR(3600/O$excelRow,\"\")"
        row.cell(16).cellFormula = "3600*(1-20%)/O$excelRow"
        row.cell(17).cellFormula = "IFERROR(VLOOKUP(A$excelRow,\$A\$19:\$H\$34,8,0),\"\")"
        row.cell(18).cellFormula = "IFERROR(Q$excelRow/R$excelRow,\"\")"

        val firstInOperation = index == 0 || all[index - 1].position != all[index].position
        if (!firstInOperation) {
            row.cell(19).setBlank(); row.cell(20).setBlank(); row.cell(21).setBlank(); row.cell(22).setBlank()
            return
        }
        val endIndex = all.indexOfLastFrom(index) { it.position == all[index].position }
        val endExcelRow = (documentRow(index = endIndex, dataStart = excelRow - index))
        row.cell(19).cellFormula = "\$D\$12"
        row.cell(20).cellFormula = "SUM(Q$excelRow:Q$endExcelRow)*9"
        row.cell(21).cellFormula = "U$excelRow/T$excelRow"
        row.cell(22).cellFormula = "AVERAGE(O$excelRow:O$endExcelRow)"
    }

    private fun documentRow(index: Int, dataStart: Int): Int = dataStart + index

    private inline fun <T> List<T>.indexOfLastFrom(start: Int, predicate: (T) -> Boolean): Int {
        var i = start
        while (i + 1 < size && predicate(this[i + 1])) i++
        return i
    }

    private fun cloneRowStyle(source: Row, target: Row) {
        target.height = source.height
        for (c in 0..22) {
            val src = source.getCell(c) ?: continue
            val dst = target.getCell(c) ?: target.createCell(c)
            dst.cellStyle = src.cellStyle
        }
    }

    private fun detectLastStudyRow(sheet: org.apache.poi.ss.usermodel.Sheet, start: Int): Int {
        var last = start - 1
        var empty = 0
        for (r in start..sheet.lastRowNum) {
            val row = sheet.getRow(r)
            val has = row?.let {
                it.text(2).isNotBlank() || it.text(4).isNotBlank() || it.numericOrNull(0) != null
            } ?: false
            if (has) { last = r; empty = 0 } else {
                empty++
                if (empty >= 4 && last >= start) break
            }
        }
        return maxOf(last, start)
    }

    private fun findHeaderRow(sheet: org.apache.poi.ss.usermodel.Sheet): Int {
        for (r in 0..minOf(sheet.lastRowNum, 120)) {
            val row = sheet.getRow(r) ?: continue
            val texts = (0..minOf(row.lastCellNum.toInt().coerceAtLeast(0), 30)).map { row.text(it).lowercase(Locale.ROOT) }
            val hasT1 = texts.any { it.startsWith("t1") }
            val hasOperator = texts.any { it.contains("operarios") || it == "operario" }
            if (hasT1 && hasOperator) return r
        }
        return -1
    }

    private fun queryName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return null
    }

    private fun Row.cell(index: Int): Cell = getCell(index) ?: createCell(index)

    private fun Row.text(index: Int): String {
        val cell = getCell(index) ?: return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                val n = cell.numericCellValue
                if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
            }
            CellType.FORMULA -> when (cell.cachedFormulaResultType) {
                CellType.STRING -> cell.stringCellValue
                CellType.NUMERIC -> cell.numericCellValue.toString()
                else -> ""
            }
            else -> ""
        }
    }

    private fun Row.numericOrNull(index: Int): Double? {
        val cell = getCell(index) ?: return null
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> cell.stringCellValue.replace(',', '.').toDoubleOrNull()
            CellType.FORMULA -> if (cell.cachedFormulaResultType == CellType.NUMERIC) cell.numericCellValue else null
            else -> null
        }
    }
}
