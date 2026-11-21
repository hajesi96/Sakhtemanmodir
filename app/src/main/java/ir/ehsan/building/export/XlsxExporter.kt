package ir.ehsan.building.export

import android.content.Context
import ir.ehsan.building.data.*
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxExporter {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun col(n: Int): String {
        var x = n; var out = ""
        while (x > 0) { val r = (x - 1) % 26; out = ('A'.code + r).toChar() + out; x = (x - 1) / 26 }
        return out
    }
    private fun cell(ref: String, value: String, numeric: Boolean = false): String =
        if (numeric) "<c r=\"$ref\"><v>${value.toLongOrNull() ?: 0}</v></c>"
        else "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(value)}</t></is></c>"

    fun export(context: Context, month: String, units: List<UnitEntity>, residents: List<ResidentEntity>, payments: List<PaymentEntity>, expenses: List<ExpenseEntity>, waterBill: WaterBillEntity?, waterAllocations: List<WaterAllocationEntity>): File {
        val rows = mutableListOf<List<Pair<String, Boolean>>>()
        rows += listOf("گزارش مالی ساختمان" to false, "ماه: $month" to false)
        rows += listOf("تعداد واحدها" to false, units.size.toString() to true, "تعداد ساکنان" to false, residents.size.toString() to true)
        rows += listOf("جمع شارژ ثابت ماهانه" to false, units.sumOf { it.fixedCharge }.toString() to true, "جمع دریافتی" to false, payments.sumOf { it.amount }.toString() to true)
        rows += listOf("جمع هزینه‌ها" to false, expenses.sumOf { it.amount }.toString() to true, "مانده خالص" to false, (payments.sumOf { it.amount } - expenses.sumOf { it.amount }).toString() to true)
        rows.add(emptyList())
        rows += listOf("قبض آب کل" to false, (waterBill?.totalAmount ?: 0L).toString() to true, "تعداد نفرات مبنا" to false, waterAllocations.sumOf { it.residentCount }.toString() to true)
        rows += listOf("سهم آب واحد" to false, "تعداد ساکنان زمان ثبت قبض" to false, "مبلغ سهم (تومان)" to false)
        waterAllocations.forEach { a -> rows += listOf("واحد ${units.find { it.id == a.unitId }?.number ?: 0}" to false, a.residentCount.toString() to true, a.amount.toString() to true) }
        rows.add(emptyList())
        rows += listOf("واحد" to false, "مالک" to false, "مستأجر" to false, "تعداد ساکن" to false, "شارژ ثابت" to false, "پرداخت این ماه" to false, "سهم آب (تعداد نفر)" to false)
        units.forEach { u ->
            val people = residents.count { it.unitId == u.id }
            val paid = payments.filter { it.unitId == u.id }.sumOf { it.amount }
            rows += listOf("${u.number}" to false, u.ownerName to false, u.tenantName to false, people.toString() to true, u.fixedCharge.toString() to true, paid.toString() to true, people.toString() to true)
        }
        rows.add(emptyList())
        rows += listOf("هزینه‌ها" to false, "دسته‌بندی" to false, "مبلغ" to false, "تاریخ" to false, "شرح" to false)
        expenses.forEach { e -> rows += listOf(e.title to false, e.category to false, e.amount.toString() to true, e.dateText to false, e.description to false) }
        rows.add(emptyList())
        rows += listOf("دریافتی‌ها" to false, "واحد" to false, "مبلغ" to false, "تاریخ" to false, "شرح" to false)
        payments.forEach { p -> rows += listOf("پرداخت" to false, (units.find { it.id == p.unitId }?.number ?: 0).toString() to true, p.amount.toString() to true, p.dateText to false, p.description to false) }

        val sheetRows = rows.mapIndexed { ri, row ->
            if (row.isEmpty()) "<row r=\"${ri + 1}\"/>" else "<row r=\"${ri + 1}\">" + row.mapIndexed { ci, pair -> cell("${col(ci + 1)}${ri + 1}", pair.first, pair.second) }.joinToString("") + "</row>"
        }.joinToString("")
        val sheet = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"/></sheetViews><sheetFormatPr defaultRowHeight=\"20\"/><sheetData>$sheetRows</sheetData></worksheet>"
        val file = File(context.getExternalFilesDir(null), "gozaresh-$month.xlsx")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            fun put(path: String, content: String) { zip.putNextEntry(ZipEntry(path)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>")
            put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            put("xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"گزارش ماهانه\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>")
            put("xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>")
            put("xl/worksheets/sheet1.xml", sheet)
        }
        return file
    }
}
