package ir.ehsan.building

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import ir.ehsan.building.data.*
import ir.ehsan.building.export.XlsxExporter
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val Navy = Color(0xFF142B4A)
private val Blue = Color(0xFF2563EB)
private val Pale = Color(0xFFF3F6FB)
private val Green = Color(0xFF15966A)
private val Red = Color(0xFFD94848)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(20, 43, 74)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Pale, surface = Color.White)) { BuildingApp() } }
    }
}

private fun money(v: Long): String = NumberFormat.getNumberInstance(Locale("fa", "IR")).format(v) + " تومان"

private fun currentMonth(): String {
    val cal = android.icu.util.Calendar.getInstance(
        android.icu.util.ULocale("fa_IR@calendar=persian")
    )
    val year = cal.get(android.icu.util.Calendar.YEAR)
    val month = cal.get(android.icu.util.Calendar.MONTH) + 1

    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}"
}

private fun currentSolarDate(): String {
    val cal = android.icu.util.Calendar.getInstance(
        android.icu.util.ULocale("fa_IR@calendar=persian")
    )
    val year = cal.get(android.icu.util.Calendar.YEAR)
    val month = cal.get(android.icu.util.Calendar.MONTH) + 1
    val day = cal.get(android.icu.util.Calendar.DAY_OF_MONTH)

    return "${year.toString().padStart(4, '0')}/${month.toString().padStart(2, '0')}/${day.toString().padStart(2, '0')}"
}


@Composable
fun BuildingApp() {
    val context = LocalContext.current
    val db = remember { BuildingDatabase.get(context) }
    val dao = remember { db.dao() }
    val scope = rememberCoroutineScope()
    val units by dao.units().collectAsState(initial = emptyList())
    val residents by dao.residents().collectAsState(initial = emptyList())
    val payments by dao.payments().collectAsState(initial = emptyList())
    val expenses by dao.expenses().collectAsState(initial = emptyList())
    var tab by remember { mutableStateOf("نمای کلی") }
    var dialog by remember { mutableStateOf("") }
    var month by remember { mutableStateOf(currentMonth()) }
    var notice by remember { mutableStateOf("") }
    val waterBills by dao.waterBills().collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        if (dao.unitCount() == 0) (1..16).forEach { dao.insertUnit(UnitEntity(number = it)) }
    }

    Scaffold(containerColor = Pale, topBar = {
        Column(Modifier.background(Navy).fillMaxWidth().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("مدیریت ساختمان", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("پنل هوشمند مدیر ساختمان", color = Color(0xFFB9C9DF), fontSize = 12.sp)
                }
                Icon(Icons.Default.Apartment, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("نمای کلی", "واحدها", "قبض آب", "دریافت‌ها", "هزینه‌ها", "گزارش ماهانه", "تنظیمات").forEach { item ->
                    FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White, selectedLabelColor = Navy, labelColor = Color(0xFFE0E9F5)))
                }
            }
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                "نمای کلی" -> Dashboard(units, residents, payments, expenses, month, onNavigate = { tab = it })
                "واحدها" -> UnitScreen(units, residents, payments, onAdd = { dialog = "unit" }, onDelete = { id -> scope.launch { dao.deleteUnit(id) } })
                "قبض آب" -> WaterBillScreen(month, { month = it }, units, residents, waterBills, onSave = { m, total ->
                    scope.launch {
                        val counts = units.associate { it.id to it.peopleCount }
                        val people = counts.values.sum()
                        if (people == 0) { notice = "تعداد نفرات واحدها را در بخش واحدها ثبت کنید" } else {
                            val allocations = mutableListOf<WaterAllocationEntity>()
                            var allocated = 0L
                            val active = units.filter { (counts[it.id] ?: 0) > 0 }
                            active.forEachIndexed { index, u ->
                                val count = counts[u.id] ?: 0
                                val amount = if (index == active.lastIndex) total - allocated else (total.toDouble() * count / people).toLong()
                                allocated += amount
                                allocations += WaterAllocationEntity(m, u.id, count, amount)
                            }
                            dao.saveWaterBill(WaterBillEntity(m, total))
                            dao.clearWaterAllocations(m)
                            dao.saveWaterAllocations(allocations)
                            notice = "قبض آب ذخیره شد و سهم واحدها محاسبه شد"
                        }
                    }
                })
                "دریافت‌ها" -> PaymentScreen(payments, units, onAdd = { dialog = "payment" }, onDelete = { id -> scope.launch { dao.deletePayment(id) } })
                "هزینه‌ها" -> ExpenseScreen(expenses, onAdd = { dialog = "expense" }, onDelete = { id -> scope.launch { dao.deleteExpense(id) } })
                "تنظیمات" -> SettingsScreen(context)
                else -> ReportScreen(month, { month = it }, units, residents, payments, expenses, onExport = {
                    scope.launch {
                        try {
                            val file = XlsxExporter.export(context, month, dao.allUnits(), dao.allResidents(), dao.paymentsForMonth(month), dao.expensesForMonth(month), dao.waterBill(month), dao.waterAllocations(month))
                            val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply { type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                            context.startActivity(Intent.createChooser(intent, "ارسال گزارش اکسل"))
                        } catch (e: Exception) { notice = "خطا در ساخت گزارش: ${e.localizedMessage}" }
                    }
                })
            }
        }
        if (dialog.isNotEmpty()) EntryDialog(dialog, units, onDismiss = { dialog = "" }, onSave = { values ->
            scope.launch {
                try {
                    when (dialog) {
                        "unit" -> dao.insertUnit(UnitEntity(number = values[0].toInt(), ownerName = values[1], tenantName = values[2], phone = values[3], fixedCharge = values[4].toLongOrNull() ?: 0, peopleCount = values[5].toIntOrNull() ?: 0))
                        "resident" -> { val u = units.find { it.number == values[0].toIntOrNull() }; if (u != null) dao.insertResident(ResidentEntity(unitId = u.id, name = values[1], phone = values[2])) else notice = "شماره واحد پیدا نشد" }
                        "payment" -> { val u = units.find { it.number == values[0].toIntOrNull() }; if (u != null) dao.insertPayment(PaymentEntity(unitId = u.id, amount = values[1].toLongOrNull() ?: 0, monthKey = values[2], dateText = values[3], description = values[4])) else notice = "شماره واحد پیدا نشد" }
                        "expense" -> dao.insertExpense(ExpenseEntity(title = values[0], category = values[1], amount = values[2].toLongOrNull() ?: 0, monthKey = values[3], dateText = values[4], description = values[5]))
                    }
                } finally { dialog = "" }
            }
        })
        if (notice.isNotEmpty()) AlertDialog(onDismissRequest = { notice = "" }, confirmButton = { TextButton(onClick = { notice = "" }) { Text("باشه") } }, text = { Text(notice) })
    }
}

@Composable
private fun Dashboard(
    units: List<UnitEntity>,
    residents: List<ResidentEntity>,
    payments: List<PaymentEntity>,
    expenses: List<ExpenseEntity>,
    month: String,
    onNavigate: (String) -> Unit
) {
    val monthPayments = payments.filter { it.monthKey == month }
    val monthExpenses = expenses.filter { it.monthKey == month }
    val paid = monthPayments.sumOf { it.amount }
    val spent = monthExpenses.sumOf { it.amount }
    val net = paid - spent
    val occupied = units.count { it.peopleCount > 0 }
    val totalPeople = units.sumOf { it.peopleCount }
    val progress = if (paid + spent == 0L) 0f else (paid.toFloat() / (paid + spent).toFloat()).coerceIn(0f, 1f)

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Navy), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("خوش آمدید", color = Color(0xFFB9C9DF), fontSize = 12.sp)
                        Text("مدیر ساختمان", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("گزارش عملکرد · $month", color = Color(0xFFDBEAFE), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.Apartment, null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("تعداد ساکنان", totalPeople.toString(), Icons.Default.People, Color(0xFF7048D8), Modifier.weight(1f))
                MetricCard("واحدهای دارای ساکن", "$occupied از ${units.size}", Icons.Default.Home, Green, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("دریافتی این ماه", money(paid), Icons.Default.AccountBalanceWallet, Color(0xFFE99A16), Modifier.weight(1f))
                MetricCard("هزینه این ماه", money(spent), Icons.Default.ReceiptLong, Red, Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("وضعیت مالی ساختمان", color = Navy, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.BarChart, null, tint = Blue)
                    }
                    Text("مانده خالص", color = Color.Gray, fontSize = 12.sp)
                    Text(money(net), color = if (net >= 0) Green else Red, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(9.dp), color = Green, trackColor = Color(0xFFFFE0E0))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("دریافت: ${money(paid)}", color = Green, fontSize = 11.sp)
                        Text("هزینه: ${money(spent)}", color = Red, fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WaterDrop, null, tint = Blue, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("اطلاعات قبض آب", color = Navy, fontWeight = FontWeight.Bold)
                        Text("مبنای تقسیم: تعداد ساکنان هر واحد", color = Color.Gray, fontSize = 11.sp)
                        Text("تعداد ساکنان ثبت‌شده: $totalPeople نفر", color = Blue, fontSize = 12.sp)
                    }
                    TextButton(onClick = { onNavigate("واحدها") }) { Text("بررسی") }
                }
            }
        }
        item { Text("دسترسی سریع", color = Navy, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction("مدیریت واحدها", Icons.Default.Home, Blue, Modifier.weight(1f)) { onNavigate("واحدها") }
                    QuickAction("ثبت دریافت", Icons.Default.AccountBalanceWallet, Green, Modifier.weight(1f)) { onNavigate("دریافت‌ها") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction("ثبت هزینه", Icons.Default.Receipt, Color(0xFFED8A13), Modifier.weight(1f)) { onNavigate("هزینه‌ها") }
                    QuickAction("گزارش و اکسل", Icons.Default.Assessment, Color(0xFF7048D8), Modifier.weight(1f)) { onNavigate("گزارش ماهانه") }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = tint)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(23.dp))
            Spacer(Modifier.height(9.dp)); Text(title, color = Color.Gray, fontSize = 12.sp)
            Text(value, color = Navy, fontWeight = FontWeight.Bold, fontSize = if (value.length > 14) 13.sp else 19.sp)
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String, action: String, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = Navy, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Color.Gray, fontSize = 11.sp) }
        Button(onClick = onAdd, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text(action) }
    }
}

@Composable
private fun UnitScreen(units: List<UnitEntity>, residents: List<ResidentEntity>, payments: List<PaymentEntity>, onAdd: () -> Unit, onDelete: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { PageHeader("مدیریت واحدها", "ثبت مالک، مستأجر و شارژ ثابت هر واحد", "واحد جدید", onAdd) }
        items(units, key = { it.id }) { u ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(Pale, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text(u.number.toString(), color = Blue, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("واحد ${u.number}", color = Navy, fontWeight = FontWeight.Bold)
                        Text("مالک: ${u.ownerName.ifBlank { "ثبت نشده" }}  ·  مستأجر: ${u.tenantName.ifBlank { "ندارد" }}", fontSize = 11.sp, color = Color.Gray)
                        Text("تعداد نفرات: ${u.peopleCount} نفر  |  شارژ: ${money(u.fixedCharge)}", fontSize = 11.sp, color = Green)
                        if (u.phone.isNotBlank()) Text("تماس: ${u.phone}", fontSize = 11.sp, color = Color.Gray)
                    }
                    IconButton(onClick = { onDelete(u.id) }) { Icon(Icons.Default.DeleteOutline, null, tint = Red) }
                }
            }
        }
    }
}

@Composable
private fun ResidentScreen(residents: List<ResidentEntity>, units: List<UnitEntity>, onAdd: () -> Unit, onDelete: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { PageHeader("مدیریت ساکنان", "امکان ثبت چند نفر برای هر واحد", "ساکن جدید", onAdd) }
        items(residents, key = { it.id }) { r ->
            val unitNo = units.find { it.id == r.unitId }?.number ?: 0
            SimpleRow("${r.name} · واحد $unitNo", r.phone.ifBlank { "شماره تماس ثبت نشده" }, onDelete = { onDelete(r.id) })
        }
        if (residents.isEmpty()) item { EmptyState("هنوز ساکنی ثبت نشده است") }
    }
}

@Composable
private fun PaymentScreen(payments: List<PaymentEntity>, units: List<UnitEntity>, onAdd: () -> Unit, onDelete: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { PageHeader("دریافت‌ها و پرداختی‌ها", "ثبت شارژهای واریزشده توسط واحدها", "ثبت دریافت", onAdd) }
        items(payments, key = { it.id }) { p ->
            val no = units.find { it.id == p.unitId }?.number ?: 0
            SimpleRow("واحد $no · ${money(p.amount)}", "${p.monthKey}  |  ${p.dateText}  ${p.description}", onDelete = { onDelete(p.id) })
        }
        if (payments.isEmpty()) item { EmptyState("هنوز دریافتی ثبت نشده است") }
    }
}

@Composable
private fun ExpenseScreen(expenses: List<ExpenseEntity>, onAdd: () -> Unit, onDelete: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { PageHeader("هزینه‌های ساختمان", "ثبت و دسته‌بندی مخارج", "هزینه جدید", onAdd) }
        items(expenses, key = { it.id }) { e -> SimpleRow("${e.title} · ${money(e.amount)}", "${e.category}  |  ${e.monthKey}  |  ${e.dateText}", onDelete = { onDelete(e.id) }) }
        if (expenses.isEmpty()) item { EmptyState("هنوز هزینه‌ای ثبت نشده است") }
    }
}

@Composable
private fun SimpleRow(title: String, subtitle: String, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 5.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(8.dp)) { Text(title, color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text(subtitle, color = Color.Gray, fontSize = 11.sp) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, null, tint = Red) }
        }
    }
}

@Composable
private fun EmptyState(text: String) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text(text, color = Color.Gray) } }

@Composable
private fun WaterBillScreen(
    month: String,
    onMonth: (String) -> Unit,
    units: List<UnitEntity>,
    residents: List<ResidentEntity>,
    bills: List<WaterBillEntity>,
    onSave: (String, Long) -> Unit
) {
    var amountText by remember(month, bills) { mutableStateOf(bills.find { it.monthKey == month }?.totalAmount?.toString() ?: "") }
    val people = units.sumOf { it.peopleCount }
    val amount = amountText.toLongOrNull()?.coerceAtLeast(0) ?: 0L
    val perPerson = if (people > 0) amount.toDouble() / people else 0.0
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("محاسبه قبض آب", color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("تقسیم بر اساس تعداد ساکنان ثبت‌شده در هر واحد", color = Color.Gray, fontSize = 12.sp)
        }
        item {
            OutlinedTextField(value = month, onValueChange = onMonth, label = { Text("ماه قبض شمسی (مثلاً 1405-08)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter(Char::isDigit) }, label = { Text("مبلغ کل قبض (تومان)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("تعداد کل ساکنان: $people نفر", color = Navy, fontWeight = FontWeight.Bold)
                    Text("سهم تقریبی هر نفر: ${money(perPerson.toLong())}", color = Blue, fontWeight = FontWeight.Bold)
                    Text("مبلغ قبض: ${money(amount)}", color = Green)
                }
            }
        }
        items(units, key = { it.id }) { unit ->
            val count = unit.peopleCount
            val share = if (people > 0) (amount.toDouble() * count / people).toLong() else 0L
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("واحد ${unit.number}", color = Navy, fontWeight = FontWeight.Bold)
                        Text("$count نفر", color = Color.Gray, fontSize = 12.sp)
                    }
                    Text(money(share), color = if (count > 0) Green else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        item {
            Button(onClick = { onSave(month, amount) }, enabled = month.matches(Regex("\\d{4}-\\d{2}")) && amount > 0 && people > 0, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("ذخیره قبض و سهم واحدها")
            }
            Text("با ذخیره قبض، تعداد ساکنان و سهم هر واحد برای همان ماه ثبت می‌شود تا تغییرات بعدی ساکنان، گزارش ماه‌های قبل را تغییر ندهد.", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ReportScreen(month: String, onMonth: (String) -> Unit, units: List<UnitEntity>, residents: List<ResidentEntity>, payments: List<PaymentEntity>, expenses: List<ExpenseEntity>, onExport: () -> Unit) {
    val monthPayments = payments.filter { it.monthKey == month }
    val monthExpenses = expenses.filter { it.monthKey == month }
    val paid = monthPayments.sumOf { it.amount }; val spent = monthExpenses.sumOf { it.amount }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("گزارش ماهانه", color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("گزارش مالی قابل خروجی برای اکسل", color = Color.Gray, fontSize = 12.sp) }
        item { OutlinedTextField(value = month, onValueChange = onMonth, label = { Text("ماه گزارش شمسی (مثلاً 1405-08)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(13.dp)) }
        item { MetricCard("جمع شارژ ثابت", money(units.sumOf { it.fixedCharge }), Icons.Default.Payments, Blue, Modifier.fillMaxWidth()) }
        item { MetricCard("تعداد نفرات", units.sumOf { it.peopleCount }.toString(), Icons.Default.People, Green, Modifier.fillMaxWidth()) }
        item { MetricCard("دریافتی ماه", money(paid), Icons.Default.AccountBalanceWallet, Green, Modifier.fillMaxWidth()) }
        item { MetricCard("هزینه ماه", money(spent), Icons.Default.ReceiptLong, Red, Modifier.fillMaxWidth()) }
        item { MetricCard("مانده خالص", money(paid - spent), Icons.Default.Analytics, Navy, Modifier.fillMaxWidth()) }
        item { Button(onClick = onExport, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("ساخت و اشتراک فایل Excel") } }
        item { Text("در فایل اکسل، خلاصه مالی، فهرست واحدها، تعداد ساکنان، سهم آب بر مبنای نفرات، دریافت‌ها و هزینه‌ها درج می‌شود.", color = Color.Gray, fontSize = 12.sp) }
    }
}

@Composable
private fun SettingsScreen(context: android.content.Context) {
    val prefs = remember { context.getSharedPreferences("building_settings", android.content.Context.MODE_PRIVATE) }
    var buildingName by remember { mutableStateOf(prefs.getString("building_name", "ساختمان ما") ?: "ساختمان ما") }
    var reminders by remember { mutableStateOf(prefs.getBoolean("reminders", true)) }
    var compact by remember { mutableStateOf(prefs.getBoolean("compact", false)) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("تنظیمات برنامه", color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("تغییرات این بخش روی همین دستگاه ذخیره می‌شوند.", color = Color.Gray, fontSize = 12.sp) }
        item { Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("اطلاعات ساختمان", color = Navy, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = buildingName, onValueChange = { buildingName = it; prefs.edit().putString("building_name", it).apply() }, label = { Text("نام ساختمان") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        } } }
        item { Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("شخصی‌سازی", color = Navy, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("نمای فشرده", modifier = Modifier.weight(1f), color = Navy); Switch(checked = compact, onCheckedChange = { compact = it; prefs.edit().putBoolean("compact", it).apply() }) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("یادآوری‌ها", modifier = Modifier.weight(1f), color = Navy); Switch(checked = reminders, onCheckedChange = { reminders = it; prefs.edit().putBoolean("reminders", it).apply() }) }
        } } }
        item { Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("درباره برنامه", color = Navy, fontWeight = FontWeight.Bold)
            Text("مدیریت ساختمان · نسخه ۱.۲.۰", color = Blue)
            Text("اطلاعات واحدها و امور مالی در پایگاه داده محلی دستگاه نگهداری می‌شوند.", color = Color.Gray, fontSize = 11.sp)
        } } }
    }
}

@Composable
private fun EntryDialog(type: String, units: List<UnitEntity>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    val fields = when (type) {
        "unit" -> listOf("شماره واحد", "نام مالک", "نام مستأجر", "شماره تماس", "شارژ ثابت (تومان)", "تعداد نفرات")
        "resident" -> listOf("شماره واحد", "نام ساکن", "شماره تماس")
        "payment" -> listOf("شماره واحد", "مبلغ (تومان)", "ماه شمسی (1405-08)", "تاریخ", "شرح")
        else -> listOf("عنوان هزینه", "دسته‌بندی", "مبلغ (تومان)", "ماه شمسی (1405-08)", "تاریخ", "شرح")
    }
    val values = remember(type) { fields.map { if (it.startsWith("ماه")) currentMonth() else if (it == "تاریخ") currentSolarDate() else "" }.toMutableStateList() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(when(type) { "unit" -> "افزودن واحد"; "resident" -> "افزودن ساکن"; "payment" -> "ثبت دریافت"; else -> "ثبت هزینه" }, color = Navy, fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (type == "payment") Text("شماره واحدهای موجود: ${units.joinToString("، ") { it.number.toString() }}", fontSize = 11.sp, color = Blue)
            fields.forEachIndexed { i, label -> OutlinedTextField(value = values[i], onValueChange = { values[i] = it }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)) }
        } },
        confirmButton = { Button(onClick = { onSave(values.toList()) }) { Text("ذخیره") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}
