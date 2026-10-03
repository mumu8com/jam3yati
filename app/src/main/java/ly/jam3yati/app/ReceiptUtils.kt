package ly.jam3yati.app

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File

object ReceiptUtils {
    fun createAndShareReceipt(
        context: Context,
        receiptNo: String,
        association: String,
        member: String,
        cycle: Int,
        amount: Double,
        remaining: Double
    ) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 24f
        canvas.drawText("إيصال دفع - جمعياتي", 550f, 70f, paint)
        paint.textSize = 16f
        canvas.drawText("رقم الإيصال: \$receiptNo", 550f, 120f, paint)
        canvas.drawText("الجمعية: \$association", 550f, 165f, paint)
        canvas.drawText("العضو: \$member", 550f, 210f, paint)
        canvas.drawText("الدورة: \$cycle", 550f, 255f, paint)
        canvas.drawText("المبلغ المدفوع: \$"+"{String.format("%.2f", amount)}", 550f, 300f, paint)
        canvas.drawText("المتبقي: \$"+"{String.format("%.2f", remaining)}", 550f, 345f, paint)
        canvas.drawText("تم إصدار الإيصال إلكترونيًا من تطبيق جمعياتي", 550f, 430f, paint)
        doc.finishPage(page)

        val dir = File(context.cacheDir, "receipts")
        dir.mkdirs()
        val file = File(dir, "receipt_\$receiptNo.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()

        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "مشاركة الإيصال"))
    }
}
