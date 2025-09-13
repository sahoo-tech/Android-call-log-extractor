package com.example.calllogexport

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvOutput: TextView
    private lateinit var btnExport: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // simple layout created programmatically for brevity
        tvOutput = TextView(this).apply { text = "Call log will appear here." }
        btnExport = Button(this).apply { text = "Export Call Log (visible & consensual)" }
        val layout = androidx.core.widget.NestedScrollView(this)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(tvOutput)
            addView(btnExport)
            setPadding(20,20,20,20)
        }
        layout.addView(container)
        setContentView(layout)

        btnExport.setOnClickListener {
            // Confirm the user
            AlertDialog.Builder(this)
                .setTitle("Export call log")
                .setMessage("This will read your call history and send it to the local network. Proceed?")
                .setPositiveButton("Yes") { _, _ -> checkPermissionAndExport() }
                .setNegativeButton("No", null)
                .show()
        }
    }

    private fun checkPermissionAndExport() {
        val perm = Manifest.permission.READ_CALL_LOG
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(perm), 101)
        } else {
            exportCallLog()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportCallLog()
            } else {
                Toast.makeText(this, "Permission denied. Cannot read call log.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportCallLog() {
        val cr: ContentResolver = contentResolver
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.CACHED_NAME
        )
        val cursor = cr.query(CallLog.Calls.CONTENT_URI, projection, null, null, CallLog.Calls.DATE + " DESC")
        if (cursor == null) {
            Toast.makeText(this, "Could not read call log.", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()
        sb.append("number,type,date,duration,name\n")
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        while (cursor.moveToNext()) {
            val number = cursor.getString(0) ?: ""
            val type = cursor.getInt(1)
            val dateMillis = cursor.getLong(2)
            val duration = cursor.getLong(3)
            val name = cursor.getString(4) ?: ""
            val typeStr = when (type) {
                CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                CallLog.Calls.MISSED_TYPE -> "MISSED"
                CallLog.Calls.VOICEMAIL_TYPE -> "VOICEMAIL"
                CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
                CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> "ANSWERED_EXTERNALLY"
                else -> "UNKNOWN"
            }
            val dateStr = df.format(Date(dateMillis))
            sb.append("\"$number\",\"$typeStr\",\"$dateStr\",\"$duration\",\"${name.replace("\"", "\"\"")}\")\n")
        }
        cursor.close()

        val callLogData = sb.toString()
        tvOutput.text = callLogData.take(4000) // show a portion on screen

        // Send data to localhost in a background thread
        thread {
            try {
                val url = URL("http://10.0.2.2:8080")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "text/csv")

                OutputStreamWriter(conn.outputStream).use {
                    it.write(callLogData)
                }

                val responseCode = conn.responseCode
                runOnUiThread {
                    if (responseCode == 200) {
                        Toast.makeText(this, "Data sent to localhost", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Failed to send data. Response: $responseCode", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error sending data: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}