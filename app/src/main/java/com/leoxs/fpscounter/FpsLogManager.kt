package com.leoxs.fpscounter

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FpsLogManager {

    private const val LOG_FILE_NAME = "fps_history.log"

    private fun logFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }

    fun appendSession(context: Context, avg: Int, min: Int, max: Int, durationSeconds: Long) {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr"))
        val timestamp = sdf.format(Date())
        val durationText = if (durationSeconds >= 60) {
            "${durationSeconds / 60} dk ${durationSeconds % 60} sn"
        } else {
            "$durationSeconds sn"
        }

        val line = "$timestamp — Ort: $avg FPS (Min: $min, Maks: $max) — Süre: $durationText\n"

        try {
            logFile(context).appendText(line)
        } catch (e: Exception) {
            // yazma başarısız olursa sessizce geç, uygulamayı çökertme
        }
    }

    fun readLog(context: Context): String {
        return try {
            val file = logFile(context)
            if (file.exists()) {
                // en yeni kayıtlar üstte görünsün
                file.readLines().reversed().joinToString("\n")
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun clearLog(context: Context) {
        try {
            logFile(context).writeText("")
        } catch (e: Exception) {
            // yoksay
        }
    }
}
