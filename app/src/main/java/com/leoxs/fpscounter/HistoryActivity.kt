package com.leoxs.fpscounter

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val logText = findViewById<TextView>(R.id.logText)
        loadLog(logText)

        findViewById<Button>(R.id.clearHistoryButton).setOnClickListener {
            FpsLogManager.clearLog(this)
            loadLog(logText)
        }
    }

    private fun loadLog(logText: TextView) {
        val content = FpsLogManager.readLog(this)
        logText.text = if (content.isBlank()) {
            "Henüz kayıtlı bir oturum yok.\nGöstergeyi başlatıp durdurduğunda burada geçmişi göreceksin."
        } else {
            content
        }
    }
}
