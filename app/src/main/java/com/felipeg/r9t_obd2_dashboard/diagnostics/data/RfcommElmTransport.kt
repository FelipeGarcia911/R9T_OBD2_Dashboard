package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

internal class RfcommElmTransport(private val context: Context) : ElmTransport {
    private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    override suspend fun open(adapterId: String) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw ElmTransportException(DiagnosticError.ADAPTER_UNREACHABLE)
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) throw ElmTransportException(DiagnosticError.PERMISSION_DENIED)
        if (!adapter.isEnabled) throw ElmTransportException(DiagnosticError.BLUETOOTH_DISABLED)
        val device = adapter.bondedDevices.firstOrNull { it.address == adapterId }
            ?: throw ElmTransportException(DiagnosticError.ADAPTER_UNREACHABLE)
        if (device.type == BluetoothDevice.DEVICE_TYPE_LE) {
            throw ElmTransportException(DiagnosticError.ADAPTER_UNREACHABLE)
        }
        val connection = device.createRfcommSocketToServiceRecord(SPP_UUID)
        socket = connection
        socketOperation(connection, 15_000) { connection.connect() }
    }

    override suspend fun exchange(command: String): String {
        val connection = socket ?: throw IOException("Socket is closed")
        return socketOperation(connection, 8_000) {
            connection.outputStream.write("$command\r".toByteArray(Charsets.US_ASCII))
            connection.outputStream.flush()
            readResponse(connection)
        }
    }

    private fun readResponse(connection: BluetoothSocket): String {
        val input = connection.inputStream
        val response = StringBuilder()
        repeat(8192) {
            val next = input.read()
            when (next) {
                -1 -> throw IOException("Adapter closed the stream")
                '>'.code -> return response.append('>').toString()
                else -> response.append(next.toChar())
            }
        }
        throw ElmTransportException(DiagnosticError.INVALID_RESPONSE)
    }

    override fun close() {
        val connection = socket ?: return
        socket = null
        closeSocket(connection)
    }

    private fun closeSocket(connection: BluetoothSocket) {
        try {
            connection.close()
        } catch (_: IOException) {
            // The socket is already being discarded.
        }
    }

    // BluetoothSocket.close unblocks Android's blocking connect and read calls.
    private suspend fun <T> socketOperation(
        connection: BluetoothSocket,
        timeoutMillis: Long,
        operation: () -> T,
    ): T = withTimeout(timeoutMillis) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { closeSocket(connection) }
            Dispatchers.IO.dispatch(continuation.context) {
                if (continuation.isActive) {
                    try {
                        continuation.resume(operation())
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                }
            }
        }
    }

    private companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
