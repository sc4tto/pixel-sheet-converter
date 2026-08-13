package it.sc4tto.pixelsheetconverter

import android.graphics.Color
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxExporter {
    fun write(result: ConversionResult, output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            entry(zip, "[Content_Types].xml", contentTypes())
            entry(zip, "_rels/.rels", rootRels())
            entry(zip, "xl/workbook.xml", workbook())
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRels())
            entry(zip, "xl/styles.xml", styles(result.palette))
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            writeSheet(zip, result)
            zip.closeEntry()
        }
    }

    private fun entry(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Pixel" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun styles(palette: IntArray): String {
        val fills = palette.joinToString("") { color ->
            val rgb = String.format("FF%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color))
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"$rgb\"/><bgColor indexed=\"64\"/></patternFill></fill>"
        }
        val xfs = palette.indices.joinToString("") { i ->
            "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"${i + 2}\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFill=\"1\"/>"
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="1"><numFmt numFmtId="164" formatCode=";;;"/></numFmts>
<fonts count="1"><font><sz val="10"/><name val="Arial"/></font></fonts>
<fills count="${palette.size + 2}"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>$fills</fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="${palette.size + 1}"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>$xfs</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
    }

    private fun writeSheet(zip: ZipOutputStream, result: ConversionResult) {
        fun write(text: String) = zip.write(text.toByteArray(Charsets.UTF_8))
        write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        write("<dimension ref=\"A1:${columnName(result.width)}${result.height}\"/><sheetViews><sheetView showGridLines=\"0\" workbookViewId=\"0\"/></sheetViews>")
        write("<cols><col min=\"1\" max=\"${result.width}\" width=\"0.45\" customWidth=\"1\"/></cols><sheetData>")
        for (y in 0 until result.height) {
            val row = StringBuilder("<row r=\"${y + 1}\" ht=\"3.75\" customHeight=\"1\">")
            for (x in 0 until result.width) {
                val index = result.indices[y * result.width + x]
                row.append("<c r=\"").append(columnName(x + 1)).append(y + 1)
                    .append("\" s=\"").append(index + 1).append("\" t=\"n\"><v>")
                    .append(index).append("</v></c>")
            }
            row.append("</row>"); write(row.toString())
        }
        write("</sheetData></worksheet>")
    }

    private fun columnName(column: Int): String {
        var n = column; val out = StringBuilder()
        while (n > 0) { n--; out.append(('A'.code + n % 26).toChar()); n /= 26 }
        return out.reverse().toString()
    }
}
