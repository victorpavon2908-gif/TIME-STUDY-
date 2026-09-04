package ni.fsn.timestudy.data

import android.content.Context
import android.net.Uri
import ni.fsn.timestudy.model.OperatorStudy
import ni.fsn.timestudy.model.StudyDocument
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigDecimal
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
     * Exportación en modo "overlay".
     *
     * IMPORTANTE: aquí NO se vuelve a guardar el libro con Apache POI.
     * El XLSX original se copia como paquete ZIP y sólo se modifican las celdas
     * de captura de datos de la hoja del estudio:
     *
     * E = código del trabajador
     * F = antigüedad
     * G = tiempo FSN
     * H = tiempo en operación
     * J:N = t1..t5
     *
     * No se modifica ninguna fórmula, borde, estilo, alto de fila, ancho de
     * columna, gráfico, imagen, vínculo, hoja ni configuración del libro.
     * La columna I (Operario) tampoco se toca porque en el formato original
     * contiene una fórmula VLOOKUP dependiente del código de la columna E.
     */
    fun exportWorkbook(document: StudyDocument, outputUri: Uri) {
        val source = requireNotNull(workingFile) { "No hay un archivo activo" }

        val addedRows = document.operators.filter { !it.deleted && it.originalRow == null }
        require(addedRows.isEmpty()) {
            "Hay operarios agregados que todavía no tienen una fila física en el Excel. " +
                "Para proteger fórmulas y formato no se insertarán filas automáticamente en esta exportación."
        }

        val worksheetEntry = locateWorksheetEntry(source, document.sheetName)

        context.contentResolver.openOutputStream(outputUri, "w").use { rawOutput ->
            requireNotNull(rawOutput) { "No se pudo crear el archivo de salida" }

            ZipInputStream(BufferedInputStream(FileInputStream(source))).use { zipIn ->
                ZipOutputStream(BufferedOutputStream(rawOutput)).use { zipOut ->
                    var foundWorksheet = false
                    var entry = zipIn.nextEntry

                    while (entry != null) {
                        val outEntry = ZipEntry(entry.name).apply {
                            if (entry.time >= 0L) time = entry.time
                        }
                        zipOut.putNextEntry(outEntry)

                        if (entry.name == worksheetEntry) {
                            foundWorksheet = true
                            val originalXml = zipIn.readBytes().toString(Charsets.UTF_8)
                            val patchedXml = patchStudySheet(originalXml, document)
                            zipOut.write(patchedXml.toByteArray(Charsets.UTF_8))
                        } else {
                            zipIn.copyTo(zipOut)
                        }

                        zipOut.closeEntry()
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }

                    require(foundWorksheet) {
                        "No se encontró la hoja ${document.sheetName} dentro del archivo XLSX"
                    }
                }
            }
        }
    }

    private fun patchStudySheet(originalXml: String, document: StudyDocument): String {
        var xml = originalXml

        document.operators.forEach { item ->
            val zeroBasedRow = item.originalRow ?: return@forEach
            val excelRow = zeroBasedRow + 1

            if (item.deleted) {
                // Eliminar un operario significa vaciar únicamente los datos capturables.
                // La fila, sus fórmulas y su formato permanecen exactamente en su lugar.
                listOf("E", "F", "G", "H").forEach { col ->
                    xml = patchStringCell(xml, "$col$excelRow", "")
                }
                listOf("J", "K", "L", "M", "N").forEach { col ->
                    xml = patchNumericCell(xml, "$col$excelRow", null)
                }
                return@forEach
            }

            xml = patchStringCell(xml, "E$excelRow", item.employeeCode)
            xml = patchStringCell(xml, "F$excelRow", item.seniority)
            xml = patchStringCell(xml, "G$excelRow", item.timeFsn)
            xml = patchStringCell(xml, "H$excelRow", item.timeOperation)

            item.times.forEachIndexed { index, value ->
                val column = ('J'.code + index).toChar()
                xml = patchNumericCell(xml, "$column$excelRow", value)
            }
        }

        return xml
    }

    /**
     * Sustituye sólo el contenido de una celda existente.
     * Si por seguridad detecta que esa celda contiene una fórmula, no la toca.
     * Conserva atributos como r= y s=, por lo que el estilo/borde original queda intacto.
     */
    private fun patchStringCell(xml: String, cellRef: String, value: String): String {
        val match = findCell(xml, cellRef)
            ?: error("No encontré la celda $cellRef en el formato original")

        if (containsFormula(match.value)) return xml

        val startTag = normalizedStartTag(match.value, cellType = if (value.isBlank()) null else "inlineStr")
        val replacement = if (value.isBlank()) {
            "$startTag</c>"
        } else {
            val preserve = if (value.firstOrNull()?.isWhitespace() == true || value.lastOrNull()?.isWhitespace() == true) {
                " xml:space=\"preserve\""
            } else {
                ""
            }
            "$startTag<is><t$preserve>${escapeXml(value)}</t></is></c>"
        }

        return xml.replaceRange(match.range, replacement)
    }

    private fun patchNumericCell(xml: String, cellRef: String, value: Double?): String {
        val match = findCell(xml, cellRef)
            ?: error("No encontré la celda $cellRef en el formato original")

        if (containsFormula(match.value)) return xml

        val startTag = normalizedStartTag(match.value, cellType = null)
        val replacement = if (value == null) {
            "$startTag</c>"
        } else {
            "$startTag<v>${formatNumber(value)}</v></c>"
        }

        return xml.replaceRange(match.range, replacement)
    }

    private fun findCell(xml: String, cellRef: String): MatchResult? {
        val ref = Regex.escape(cellRef)
        val pattern = Regex(
            "<c\\b[^>]*\\br=\"$ref\"[^>]*(?:/>|>.*?</c>)",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        return pattern.find(xml)
    }

    private fun containsFormula(cellXml: String): Boolean =
        Regex("<f(?:\\s|>)").containsMatchIn(cellXml)

    private fun normalizedStartTag(cellXml: String, cellType: String?): String {
        val raw = if (cellXml.contains("/>" ) && !cellXml.contains("</c>")) {
            cellXml.substringBefore("/>")
        } else {
            cellXml.substringBefore('>')
        }

        var tag = raw.removeSuffix("/")
        tag = tag.replace(Regex("\\s+t=\"[^\"]*\""), "")

        if (cellType != null) {
            tag += " t=\"$cellType\""
        }

        return "$tag>"
    }

    private fun formatNumber(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun locateWorksheetEntry(file: File, sheetName: String): String {
        val workbookXml = readZipText(file, "xl/workbook.xml")
        val relationshipsXml = readZipText(file, "xl/_rels/workbook.xml.rels")

        val sheetTag = Regex("<sheet\\b[^>]*/?>")
            .findAll(workbookXml)
            .firstOrNull { xmlAttribute(it.value, "name") == sheetName }
            ?.value
            ?: error("No encontré la hoja $sheetName en workbook.xml")

        val relationshipId = xmlAttribute(sheetTag, "r:id")
            ?: error("La hoja $sheetName no tiene relación interna")

        val relationshipTag = Regex("<Relationship\\b[^>]*/?>")
            .findAll(relationshipsXml)
            .firstOrNull { xmlAttribute(it.value, "Id") == relationshipId }
            ?.value
            ?: error("No encontré la relación $relationshipId de la hoja $sheetName")

        val target = xmlAttribute(relationshipTag, "Target")
            ?: error("La relación $relationshipId no tiene destino")

        return when {
            target.startsWith("/") -> target.removePrefix("/")
            target.startsWith("xl/") -> target
            else -> "xl/${target.removePrefix("./")}" 
        }
    }

    private fun xmlAttribute(tag: String, name: String): String? {
        val escapedName = Regex.escape(name)
        return Regex("(?:\\s|^)$escapedName=\"([^\"]*)\"")
            .find(tag)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun readZipText(file: File, entryName: String): String {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(entryName)
                ?: error("El XLSX no contiene $entryName")
            return zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    private fun findHeaderRow(sheet: org.apache.poi.ss.usermodel.Sheet): Int {
        for (r in 0..minOf(sheet.lastRowNum, 120)) {
            val row = sheet.getRow(r) ?: continue
            val texts = (0..minOf(row.lastCellNum.toInt().coerceAtLeast(0), 30))
                .map { row.text(it).lowercase(Locale.ROOT) }
            val hasT1 = texts.any { it.startsWith("t1") }
            val hasOperator = texts.any { it.contains("operarios") || it == "operario" }
            if (hasT1 && hasOperator) return r
        }
        return -1
    }

    private fun queryName(uri: Uri): String? {
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return null
    }

    private fun Row.text(index: Int): String {
        val cell = getCell(index) ?: return ""
        return when (cell.cellTypeEnum) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                val n = cell.numericCellValue
                if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
            }
            CellType.FORMULA -> when (cell.cachedFormulaResultTypeEnum) {
                CellType.STRING -> cell.stringCellValue
                CellType.NUMERIC -> cell.numericCellValue.toString()
                else -> ""
            }
            else -> ""
        }
    }

    private fun Row.numericOrNull(index: Int): Double? {
        val cell = getCell(index) ?: return null
        return when (cell.cellTypeEnum) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> cell.stringCellValue.replace(',', '.').toDoubleOrNull()
            CellType.FORMULA -> {
                if (cell.cachedFormulaResultTypeEnum == CellType.NUMERIC) cell.numericCellValue else null
            }
            else -> null
        }
    }
}
