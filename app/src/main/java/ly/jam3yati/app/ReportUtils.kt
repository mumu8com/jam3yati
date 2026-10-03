package ly.jam3yati.app

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ReportUtils {
    private fun share(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.startActivity(Intent.createChooser(intent, title))
    }
    fun createPdfReport(context: Context, association: JamAssociation, items: List<JamInstallment>) {
        val doc = PdfDocument()
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        var y = 55f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; textAlign = Paint.Align.RIGHT }
        page.canvas.drawText("تقرير دفعات - " + association.name, 550f, y, paint); y += 35
        page.canvas.drawText("القسط: " + association.installment + " | الأعضاء: " + association.memberCount, 550f, y, paint); y += 35
        items.forEach { item ->
            if (y > 790f) { doc.finishPage(page); pageNumber++; page = doc.startPage(PdfDocument.PageInfo.Builder(595,842,pageNumber).create()); y=55f }
            page.canvas.drawText(item.dueDate + " | " + item.memberName.ifBlank { "عضو" } + " | " + item.paidAmount + "/" + item.amount + " | " + item.status, 550f, y, paint)
            y += 26
        }
        doc.finishPage(page)
        val dir = File(context.cacheDir, "reports"); dir.mkdirs()
        val file = File(dir, "jam3yati_report_" + association.id + ".pdf")
        file.outputStream().use { doc.writeTo(it) }; doc.close()
        share(context,file,"application/pdf","مشاركة تقرير PDF")
    }
    fun createXlsxReport(context: Context, association: JamAssociation, items: List<JamInstallment>) {
        fun esc(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace(""","&quot;")
        fun cell(v: String) = "<c t=\"inlineStr\"><is><t>" + esc(v) + "</t></is></c>"
        val rows = StringBuilder()
        rows.append("<row>").append(cell("الدورة")).append(cell("العضو")).append(cell("تاريخ الاستحقاق")).append(cell("المبلغ")).append(cell("المدفوع")).append(cell("الحالة")).append("</row>")
        items.forEach { item -> rows.append("<row>").append(cell(item.cycleNumber.toString())).append(cell(item.memberName.ifBlank { "عضو" })).append(cell(item.dueDate)).append(cell(item.amount.toString())).append(cell(item.paidAmount.toString())).append(cell(item.status)).append("</row>") }
        val dir = File(context.cacheDir, "reports"); dir.mkdirs()
        val file = File(dir, "jam3yati_report_" + association.id + ".xlsx")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, data: String) { zip.putNextEntry(ZipEntry(name)); zip.write(data.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            entry("[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>")
            entry("_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            entry("xl/workbook.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"الدفعات\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>")
            entry("xl/_rels/workbook.xml.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>")
            entry("xl/worksheets/sheet1.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>" + rows.toString() + "</sheetData></worksheet>")
        }
        share(context,file,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","مشاركة تقرير Excel")
    }
}