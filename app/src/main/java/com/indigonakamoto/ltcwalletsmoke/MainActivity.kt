package com.indigonakamoto.ltcwalletsmoke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier.modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val lines = remember { mutableStateListOf<SmokeLine>() }
                    var running by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "LitecoinDevKit smoke",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "testnet4 · Electrum · UniFFI 3.1.0-litecoin.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                if (running) return@Button
                                running = true
                                lines.clear()
                                scope.launch {
                                    val collected = withContext(Dispatchers.IO) {
                                        val out = mutableListOf<SmokeLine>()
                                        SmokeRunner.run { out.add(it) }
                                        out
                                    }
                                    lines.addAll(collected)
                                    running = false
                                }
                            },
                            enabled = !running,
                        ) {
                            Text(if (running) "Running…" else "Run Smoke")
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(lines) { line ->
                                Text(
                                    text = "[${line.status}] ${line.message}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = when (line.status) {
                                        SmokeStatus.PASS -> Color(0xFF1B7F3A)
                                        SmokeStatus.FAIL -> Color(0xFFB00020)
                                        SmokeStatus.NOTE -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
