package com.example.croctrollercompass

import CompassWorker
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.example.croctrollercompass.ui.theme.CroctrollerCompassTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URI


class MainActivity : ComponentActivity(), WebSocketListener {
    private var uploadWorkRequest: WorkRequest? = null
    private var serverUrl by mutableStateOf("ws://192.168.1.134:1337/compass")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CroctrollerCompassTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "cdroctroller insane app 🤘🤘🤘🤘🤘")
                        Spacer(modifier = Modifier.height(16.dp))
                        UrlTextField(modifier = Modifier.fillMaxWidth(0.8f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { startCompassWorker(false) }) {
                            Text("Start compass worker")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            Toast.makeText(this@MainActivity, "Connecting and calibrating!", Toast.LENGTH_SHORT).show()
                            startCompassWorker(true)
                        }
                        ) {
                            Text("Start compass worker and calibrate for zero")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { stopCompassWorker() }) {
                            Text("Stop compass worker")
                        }
                    }
                }
            }
        }

    }

    @Composable
    private fun UrlTextField(modifier: Modifier) {
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            modifier = modifier
        )
    }

    private fun startCompassWorker(isCalibration: Boolean = false) {
        val inputData = Data.Builder()
            .putString("SERVER_URL", serverUrl)
            .putBoolean("CALIBRATE", isCalibration)
            .build()

        if (uploadWorkRequest != null) {
            stopCompassWorker()
        }

        uploadWorkRequest = OneTimeWorkRequestBuilder<CompassWorker>()
            .setInputData(inputData)
            .build()
        WorkManager.getInstance(applicationContext).enqueue(uploadWorkRequest!!)

        Toast.makeText(this@MainActivity, "Started compass worker!", Toast.LENGTH_SHORT).show()
    }

    private fun stopCompassWorker() {
        if (uploadWorkRequest == null) {
            Toast.makeText(this@MainActivity, "Compass worker isn't running!", Toast.LENGTH_SHORT).show()
            return
        }

        WorkManager.getInstance(applicationContext).cancelWorkById(uploadWorkRequest!!.id)
        Toast.makeText(this@MainActivity, "Stopped compass worker!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCompassWorker()
    }

    override fun onConnected() {
        Toast.makeText(this@MainActivity, "Connected to websocket server", Toast.LENGTH_SHORT).show()
    }

    override fun onMessage(message: String) {
        TODO("Not yet implemented")
    }

    override fun onDisconnected() {
        Toast.makeText(this@MainActivity, "Disconnected from websocket server", Toast.LENGTH_SHORT).show()
    }
}