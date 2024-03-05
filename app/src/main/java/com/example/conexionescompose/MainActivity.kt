package com.example.conexionescompose

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.conexionescompose.ui.theme.ConexionesComposeTheme
import java.io.IOException
import java.util.UUID


import android.annotation.SuppressLint

import android.os.Handler
import android.os.Looper

import androidx.activity.compose.setContent

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button

import androidx.compose.material3.Surface
import androidx.compose.material3.Text

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

import androidx.compose.ui.unit.dp


import java.io.InputStream
import java.io.OutputStream


class MainActivity : ComponentActivity() {
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        val status = mutableStateOf("Bluetooth & Arduino\n")
        // Controlador para enviar mensajes desde Threads
        val handler = Handler(Looper.getMainLooper()) { msg ->
            when (msg.what) {
                CONNECTION_FAILED -> {
                    status.value += "La connexion à HC-05 a échoué\n"
                    true
                }

                CONNECTION_SUCCESS -> {
                    status.value += "Connexion à HC-05 réussie\n"
                    true
                }

                else -> false
            }
        }
        val blutoothPermission = android.Manifest.permission.BLUETOOTH_CONNECT
        // registro de liberación de solicitud de permiso
        // se llamará a este lanzador si aún no se ha concedido el permiso
        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission())
            { isGranted: Boolean ->
                if (isGranted) {
                    status.value += "Permission accepte\nTentative de connexion\n"
                    status.value += connectHC05(bluetoothAdapter, handler)
                } else {
                    status.value += "====>  Permission refuse\n"
                }
            }
        //Comprueba si la aplicación ya tiene permiso
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                blutoothPermission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            status.value += "Permission déjà accordée \nTentative de connexion\n"
            status.value += connectHC05(bluetoothAdapter, handler)
        } else {
            status.value += "On va demander la permission\n"
            requestPermissionLauncher.launch(blutoothPermission)
        }
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                MyUI(status)
            }
        }
    }
}

//##################################################################################################

@SuppressLint("MissingPermission")
private fun connectHC05(bluetoothAdapter: BluetoothAdapter?, handler: Handler): String {
    // Obtener la lista de dispositivos emparejados
    val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
    // Localizar el NOVA_FINAL en la lista
    val hc05Device = pairedDevices?.find { it.name == "NOVA_FINAL" }
    // Si el HC-05 está emparejado, intentar conectar
    if (hc05Device != null) {
        ConnectThread(hc05Device, handler).start()
        // Los mensajes de estado se envían desde ConnectThread() al handler
        return ""
    } else {
        return "NOVA_FINAL No asociado\n"
    }

}

//##################################################################################################
private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
const val CONNECTION_FAILED: Int = 0
const val CONNECTION_SUCCESS: Int = 1

@SuppressLint("MissingPermission")
class ConnectThread(private val monDevice: BluetoothDevice, private val handler: Handler) :
    Thread() {
    private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
        monDevice.createRfcommSocketToServiceRecord(MY_UUID)
    }

    override fun run() {
        mmSocket?.let { socket ->
            try {
                socket.connect()
                handler.obtainMessage(CONNECTION_SUCCESS).sendToTarget()
            } catch (e: Exception) {
                handler.obtainMessage(CONNECTION_FAILED).sendToTarget()
            }
            dataExchaneInstance = DataExchange(socket)
        }
    }
}

//##################################################################################################
var dataExchaneInstance: DataExchange? = null

class DataExchange(mmSocket: BluetoothSocket) : Thread() {
    private val length = 5
    private val mmInStream: InputStream = mmSocket.inputStream
    private val mmOutStream: OutputStream = mmSocket.outputStream
    private val mmBuffer: ByteArray = ByteArray(length)

    fun write(bytes: ByteArray) {
        try {
            mmOutStream.write(bytes)
        } catch (_: IOException) {
        }
    }

    fun read(): String {
        try {
            mmOutStream.write(64)
        } catch (_: IOException) {
        }

        var numBytesReaded = 0
        try {
            while (numBytesReaded < length) {
                val num = mmInStream.read(mmBuffer, numBytesReaded, length - numBytesReaded)
                if (num == -1) {
                    // La fin du flux a été atteinte
                    break
                }
                numBytesReaded += num
            }
            return String(mmBuffer, 0, numBytesReaded)
        } catch (e: IOException) {
            return "erreur" // Retourner une chaîne vide en cas d'erreur
        }
    }
}

//The UI ##################################################################################################
@Composable
fun MyUI(connectStatus: MutableState<String>) {
    //val connectStatus = remember { mutableStateOf(status.toString()) }
    val capteur1 = remember { mutableStateOf("Rien") }
    Column {
        Text(
            text = connectStatus.value,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .background(Color(0x80E2EBEA))
                .padding(start = 16.dp)  // marge intérieure
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = {
                dataExchaneInstance?.write("A".toByteArray())
            }
            )
            {
                Text("  LED ON  ")
            }
        }
    }
}