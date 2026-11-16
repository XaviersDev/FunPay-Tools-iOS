package ru.allisighs.funpaytools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToLong

/**
 * Продажи по играм и категориям за период, с процентом сотрудникам для каждой категории.
 * Считаются только закрытые заказы (как «Всего заработано»), ожидающие показываются отдельно.
 * Проценты хранятся на устройстве: 0 % = товар выдаётся автоматически, сотруднику ничего.
 */
object SalesBreakdown {
    private const val PREFS = "sales_percent"

    enum class Period(val label: String) { TODAY("Сегодня"), YESTERDAY("Вчера"), WEEK("7 дней"), MONTH("Этот месяц"), PREV_MONTH("Прошлый месяц"), ALL("Всё время") }

    data class Row(val game: String, val category: String, val closed: Int, val sum: Double, val pending: Int, val pendingSum: Double)

    fun key(game: String, category: String) = "$game|$category"

    fun loadPercents(context: Context): Map<String, Double> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.all.mapNotNull { (k, v) -> (v as? String)?.toDoubleOrNull()?.let { k to it } }.toMap()
    }

    fun savePercent(context: Context, key: String, value: Double?) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (value == null || value <= 0.0) e.remove(key) else e.putString(key, value.toString())
        e.apply()
    }

    /** Границы периода [from, to) в миллисекундах; для «Всё время» null. */
    fun bounds(period: Period, now: Long = System.currentTimeMillis()): Pair<Long, Long>? {
        fun startOfDay(daysAgo: Int): Long {
            val c = Calendar.getInstance()
            c.timeInMillis = now
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            c.add(Calendar.DAY_OF_YEAR, -daysAgo)
            return c.timeInMillis
        }
        fun startOfMonth(monthsAgo: Int): Long {
            val c = Calendar.getInstance()
            c.timeInMillis = now
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            c.add(Calendar.MONTH, -monthsAgo)
            return c.timeInMillis
        }
        val end = now + 60_000
        return when (period) {
            Period.TODAY -> startOfDay(0) to end
            Period.YESTERDAY -> startOfDay(1) to startOfDay(0)
            Period.WEEK -> startOfDay(6) to end
            Period.MONTH -> startOfMonth(0) to end
            Period.PREV_MONTH -> startOfMonth(1) to startOfMonth(0)
            Period.ALL -> null
        }
    }

    fun compute(sales: List<FunPayRepository.SaleItem>, period: Period, now: Long = System.currentTimeMillis()): List<Row> {
        val b = bounds(period, now)
        val inPeriod = sales.filter { s ->
            if (s.status != "closed" && s.status != "paid") return@filter false
            if (b == null) return@filter true
            val t = FpDates.parse(s.date, now) ?: return@filter false
            t >= b.first && t < b.second
        }
        return inPeriod.groupBy { it.game.ifBlank { "Без игры" } to it.category.ifBlank { "Без категории" } }
            .map { (gc, l) ->
                val closed = l.filter { it.status == "closed" }
                val pending = l.filter { it.status == "paid" }
                Row(gc.first, gc.second, closed.size, closed.sumOf { it.priceValue }, pending.size, pending.sumOf { it.priceValue })
            }
            .sortedWith(compareBy<Row> { it.game.lowercase() }.thenByDescending { it.sum })
    }

    fun money(v: Double): String {
        val r = (v * 100).roundToLong()
        val whole = (r / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
        val frac = (r % 100).toString().padStart(2, '0')
        return "$whole.$frac ₽"
    }

    fun report(rows: List<Row>, percents: Map<String, Double>, period: Period): String {
        val sb = StringBuilder("Продажи по категориям: ${period.label}\n")
        var total = 0.0; var pay = 0.0
        rows.groupBy { it.game }.forEach { (game, l) ->
            sb.append("\n").append(game).append("\n")
            l.forEach { r ->
                val pct = percents[key(r.game, r.category)] ?: 0.0
                val p = r.sum * pct / 100
                total += r.sum; pay += p
                sb.append("  ").append(r.category).append(": ").append(r.closed).append(" шт, ").append(money(r.sum))
                if (pct > 0) sb.append(", сотруднику ").append(fmtPct(pct)).append("% = ").append(money(p))
                sb.append("\n")
            }
        }
        sb.append("\nИтого: ").append(money(total))
        if (pay > 0) sb.append("\nСотрудникам: ").append(money(pay)).append("\nОстаётся: ").append(money(total - pay))
        return sb.toString()
    }

    fun fmtPct(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}

@Composable
fun SalesBreakdownDialog(
    sales: List<FunPayRepository.SaleItem>,
    isFullLoad: Boolean,
    theme: AppTheme,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val accent = ThemeManager.parseColor(theme.accentColor)
    val textPri = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSec = ThemeManager.parseColor(theme.textSecondaryColor)
    val surf = ThemeManager.parseColor(theme.surfaceColor)
    val bg = ThemeManager.parseColor(theme.backgroundColor)
    val green = Color(0xFF2E7D32)

    var period by remember { mutableStateOf(SalesBreakdown.Period.TODAY) }
    val percents = remember { mutableStateMapOf<String, Double>().apply { putAll(SalesBreakdown.loadPercents(context)) } }
    val rows = remember(sales, period) { SalesBreakdown.compute(sales, period) }
    val total = rows.sumOf { it.sum }
    val pay = rows.sumOf { it.sum * (percents[SalesBreakdown.key(it.game, it.category)] ?: 0.0) / 100 }
    val pendingCount = rows.sumOf { it.pending }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(bg)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("По играм и категориям", color = textPri, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Закрытые заказы и процент сотрудникам", color = textSec, fontSize = 12.sp)
                }
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(SalesBreakdown.report(rows, percents, period)))
                    Toast.makeText(context, "Отчёт скопирован", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.ContentCopy, "Скопировать отчёт", tint = accent) }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Закрыть", tint = textPri) }
            }

            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SalesBreakdown.Period.entries.toList()) { p ->
                    val sel = p == period
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (sel) accent else surf)
                            .clickable { period = p }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text(p.label, color = if (sel) Color.White else textPri, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                }
            }

            // итоги
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("Выручка", SalesBreakdown.money(total), accent),
                    Triple("Сотрудникам", SalesBreakdown.money(pay), Color(0xFFE65100)),
                    Triple("Остаётся", SalesBreakdown.money(total - pay), green)
                ).forEach { (label, value, c) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(surf).padding(10.dp)) {
                        Text(label, color = textSec, fontSize = 11.sp)
                        Text(value, color = c, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (pendingCount > 0) Text("Ещё $pendingCount заказ(ов) ждут подтверждения покупателя, они не входят в суммы.", color = textSec, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            if (!isFullLoad) Text("Загружены не все продажи: за старые периоды суммы могут быть неполными. Дождись полной загрузки на экране продаж.", color = Color(0xFFE65100), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))

            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Нет продаж за этот период", color = textSec) }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.groupBy { it.game }.forEach { (game, list) ->
                        item(key = "g_$game") {
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(surf).padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(game, color = textPri, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                    Text("${list.sumOf { it.closed }} шт · ${SalesBreakdown.money(list.sumOf { it.sum })}", color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                list.forEach { r ->
                                    val k = SalesBreakdown.key(r.game, r.category)
                                    val pct = percents[k] ?: 0.0
                                    Spacer(Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(r.category, color = textPri, fontSize = 14.sp)
                                            Text(
                                                "${r.closed} шт · ${SalesBreakdown.money(r.sum)}" + if (pct > 0) " · сотруднику ${SalesBreakdown.money(r.sum * pct / 100)}" else "",
                                                color = textSec, fontSize = 12.sp
                                            )
                                        }
                                        PercentField(
                                            rowKey = k,
                                            value = pct,
                                            accent = accent, textPri = textPri, textSec = textSec,
                                            onChange = { v ->
                                                if (v == null || v <= 0.0) percents.remove(k) else percents[k] = v
                                                SalesBreakdown.savePercent(context, k, v)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text("Процент — доля сотрудника с продаж категории. Оставь пустым для категорий с автовыдачей. Проценты сохраняются на этом телефоне.", color = textSec, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PercentField(rowKey: String, value: Double, accent: Color, textPri: Color, textSec: Color, onChange: (Double?) -> Unit) {
    var text by remember(rowKey) { mutableStateOf(if (value > 0) SalesBreakdown.fmtPct(value) else "") }
    Row(
        Modifier.width(76.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                val t = raw.replace(',', '.').filter { it.isDigit() || it == '.' }.take(5)
                val v = t.toDoubleOrNull()
                if (t.isEmpty() || (v != null && v <= 100.0)) {
                    text = t
                    onChange(v)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = TextStyle(color = textPri, fontSize = 14.sp, textAlign = TextAlign.End),
            cursorBrush = SolidColor(accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (text.isEmpty()) Text("0", color = textSec.copy(alpha = 0.5f), fontSize = 14.sp)
                    inner()
                }
            }
        )
        Text(" %", color = textSec, fontSize = 13.sp)
    }
}
