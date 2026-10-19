package com.mystopwatch.app

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Секундомер. startedAt != null — значит, он сейчас идёт. */
data class Stopwatch(
    val id: Long,
    val name: String,
    val accumulated: Long = 0L,
    val startedAt: Long? = null
) {
    val running: Boolean get() = startedAt != null
    fun elapsed(now: Long): Long =
        (accumulated + (startedAt?.let { now - it } ?: 0L)).coerceAtLeast(0L)
}

/** Запись в истории. */
data class Record(val name: String, val date: Long, val duration: Long)

/** Сохранение данных в памяти телефона. */
class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("stopwatch_data", Context.MODE_PRIVATE)

    fun loadStopwatches(): List<Stopwatch> {
        val arr = JSONArray(prefs.getString("stopwatches", null) ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Stopwatch(
                id = o.getLong("id"),
                name = o.getString("name"),
                accumulated = o.getLong("acc"),
                startedAt = if (o.has("start")) o.getLong("start") else null
            )
        }
    }

    fun saveStopwatches(list: List<Stopwatch>) {
        val arr = JSONArray()
        list.forEach { s ->
            val o = JSONObject().put("id", s.id).put("name", s.name).put("acc", s.accumulated)
            s.startedAt?.let { o.put("start", it) }
            arr.put(o)
        }
        prefs.edit().putString("stopwatches", arr.toString()).apply()
    }

    fun loadHistory(): List<Record> {
        val arr = JSONArray(prefs.getString("history", null) ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Record(o.getString("name"), o.getLong("date"), o.getLong("dur"))
        }
    }

    fun saveHistory(list: List<Record>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().put("name", r.name).put("date", r.date).put("dur", r.duration))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }
}

fun formatTime(ms: Long): String {
    val cs = (ms / 10) % 100
    val s = (ms / 1000) % 60
    val m = (ms / 60_000) % 60
    val h = ms / 3_600_000
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d.%02d", h, m, s, cs)
    else String.format(Locale.US, "%02d:%02d.%02d", m, s, cs)
}

fun formatDate(t: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(t))

fun recordLine(r: Record): String =
    "${r.name} — ${formatTime(r.duration)} (${formatDate(r.date)})"

fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться"))
}
