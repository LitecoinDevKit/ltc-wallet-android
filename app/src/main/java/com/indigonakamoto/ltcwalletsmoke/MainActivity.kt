package com.indigonakamoto.ltcwalletsmoke

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val log = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(0, 24, 0, 0)
            setTextIsSelectable(true)
            setTextColor("#222222".toColorInt())
        }
        val runButton = Button(this).apply { text = "Run Smoke" }
        val title = TextView(this).apply {
            text = "LitecoinDevKit smoke"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "testnet4 · Electrum · UniFFI 3.1.0-litecoin.1"
            textSize = 12f
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(title)
            addView(subtitle)
            addView(runButton)
            addView(log)
        }
        setContentView(ScrollView(this).apply { addView(column) })

        runButton.setOnClickListener {
            runButton.isEnabled = false
            runButton.text = "Running…"
            log.text = ""
            lifecycleScope.launch {
                    val lines = withContext(Dispatchers.IO) {
                        val out = mutableListOf<SmokeLine>()
                        SmokeRunner.run(filesDir) {
                            // Mirrored to logcat so headless runs can capture results.
                            Log.i("LtcSmoke", "[${it.status}] ${it.message}")
                            out.add(it)
                        }
                        Log.i("LtcSmoke", "SMOKE_RUN_COMPLETE")
                        out
                    }
                val sb = StringBuilder()
                for (line in lines) {
                    sb.append('[').append(line.status).append("] ").append(line.message).append('\n')
                }
                log.text = sb.toString()
                runButton.isEnabled = true
                runButton.text = "Run Smoke"
            }
        }
    }
}
