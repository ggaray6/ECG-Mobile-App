@file:Suppress("DEPRECATION")

package com.example.dumbassapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import java.io.IOException
import android.graphics.Color
import android.widget.TextView
import android.widget.Toast
import java.util.UUID
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.bluetooth.BluetoothSocket
import androidx.core.content.ContextCompat
import java.lang.reflect.Method
import java.util.*
import java.io.InputStream
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var btDevice: BluetoothDevice
    private lateinit var textView: TextView
    private lateinit var statusTextView: TextView

    private lateinit var lineChart: LineChart

    private var connectedThread: ConnectedThread? = null

    companion object {
        private const val HC_06_MAC_ADDRESS = "98:DA:60:09:BA:5B" // Replace with your HC-06 MAC address
        private const val TAG = "BluetoothDebug"
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 123
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lineChart = findViewById(R.id.lineChart) // Initialize lineChart here

       // textView = findViewById(R.id.textView)
        statusTextView = findViewById(R.id.statusTextView)

        Log.d(TAG, "App loaded successfully")

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
        } else {
            initializeBluetooth()
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return (ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.BLUETOOTH_ADMIN
        ) == PackageManager.PERMISSION_GRANTED)
    }

    private fun requestBluetoothPermissions() {
        ActivityCompat.requestPermissions(
            this, arrayOf(
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN
            ), REQUEST_BLUETOOTH_PERMISSIONS
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeBluetooth()
            } else {
                // Handle the case when the user denies permissions
                Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Bluetooth permissions are required")
            }
        }
    }

    private fun initializeBluetooth() {
        Log.d(TAG, "Initializing Bluetooth")

        // Check if Bluetooth permissions are granted
        if (!hasBluetoothPermissions()) {
            // Request Bluetooth permissions
            requestBluetoothPermissions()
            return
        }

        // Get the bonded Bluetooth devices
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices

        // Check if any bonded devices are found
        if (pairedDevices.isNullOrEmpty()) {
            Log.d(TAG, "No paired Bluetooth devices found")
            statusTextView.text = "No paired Bluetooth devices found"
            return
        }

        // Search for the desired Bluetooth device by MAC address
        for (device in pairedDevices) {
            if (device.address == HC_06_MAC_ADDRESS) {
                btDevice = device
                Log.d(TAG, "Found HC-06 Bluetooth device")
                statusTextView.text = "Found HC-06 Bluetooth device"
                // You can perform additional actions here if needed
                break
            }
        }

        // Handle the case where the desired Bluetooth device is not found
        if (!::btDevice.isInitialized) {
            Log.d(TAG, "HC-06 Bluetooth device not found")
            statusTextView.text = "HC-06 Bluetooth device not found"
            // You can perform additional actions here if needed
        } else {
            connectToDevice(btDevice)
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "Attempting to connect to device: ${device.name}")

        // Attempt to create a BluetoothSocket using reflection
        val socket: BluetoothSocket? = createFallbackSocket(device)

        // Check if the socket was created
        if (socket == null) {
            Log.e(TAG, "Failed to create socket for device: ${device.name}")
            statusTextView.text = "Failed to create socket for device: ${device.name}"
            return
        }

        // Attempt to connect to the device
        try {
            socket.connect()
            Log.d(TAG, "Successfully connected to device: ${device.name}")
            statusTextView.text = "Successfully connected to device: ${device.name}"
            // Handle successful connection
        } catch (e: IOException) {
            // Handle connection error
            Log.e(TAG, "Error connecting to device ${device.name}: ${e.message}")
            statusTextView.text = "Error connecting to device ${device.name}: ${e.message}"
            try {
                socket.close()
            } catch (closeException: IOException) {
                Log.e(TAG, "Error closing socket: ${closeException.message}")
            }
        }
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()
    }

    private fun createFallbackSocket(device: BluetoothDevice): BluetoothSocket? {
        try {
            val clazz = device.javaClass
            val paramTypes = arrayOf(Integer.TYPE)
            val method = clazz.getMethod("createRfcommSocket", *paramTypes)
            val params = arrayOf(1)
            return method.invoke(device, *params) as? BluetoothSocket
        } catch (e: Exception) {
            Log.e(TAG, "Error creating fallback socket: ${e.message}")
        }
        return null
    }

    @Throws(IOException::class)
    private fun createBluetoothSocket(device: BluetoothDevice): BluetoothSocket? {
        return try {
            device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
        } catch (e: IOException) {
            // Attempt fallback using reflection
            Log.d(TAG, "Fallback method: creating insecure socket using reflection")
            val createRfcommSocketMethod: Method = device.javaClass.getMethod(
                "createRfcommSocket",
                *arrayOf<Class<*>>(Int::class.javaPrimitiveType!!)
            )
            val fallbackSocket: BluetoothSocket? =
                createRfcommSocketMethod.invoke(device, 1) as BluetoothSocket?
            fallbackSocket
        }
    }

    // Thread to manage the Bluetooth connection and data reading
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int

            // Create an ArrayList to store received ECG data
            val ecgData = ArrayList<Entry>()

            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer)
                    val incomingMessage = String(buffer, 0, bytes)

                    // Split the incoming message into individual ECG values
                    val ecgValues = incomingMessage.split(",")

                    // Parse and add each ECG value to the data list
                    ecgValues.forEach {
                        try {
                            val ecgValue = it.toFloat()
                            ecgData.add(Entry(ecgData.size.toFloat(), ecgValue))
                        } catch (e: NumberFormatException) {
                            Log.e(TAG, "Error parsing ECG value: ${e.message}")
                        }
                    }

                    // Update the graph with the new data
                    runOnUiThread {
                        updateGraph(ecgData)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error reading from input stream: ${e.message}")
                    break
                }
            }
        }

        private fun updateGraph(ecgData: ArrayList<Entry>) {
            // Create a LineDataSet with the received ECG data
            val dataSet = LineDataSet(ecgData, "ECG Data")

            // Customize the dataset properties (color, etc.) if needed
            dataSet.color = Color.BLUE
            dataSet.setCircleColor(Color.BLUE)
            dataSet.lineWidth = 2f
            dataSet.circleRadius = 4f
            dataSet.valueTextColor = Color.BLACK

            // Create a LineData object and add the dataset to it
            val lineData = LineData(dataSet)

            // Set the LineData to the LineChart
            lineChart.data = lineData

            // Invalidate the chart to refresh it
            lineChart.invalidate()
        }

        // Cancel the thread and close the socket
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing socket: ${e.message}")
            }
        }
    }


}
