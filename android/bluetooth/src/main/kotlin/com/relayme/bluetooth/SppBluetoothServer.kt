package com.relayme.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.relayme.core.Protocol
import java.io.IOException
import java.util.UUID

/**
 * Bluetooth Classic Serial Port Profile (SPP) server.
 *
 * Why it exists: to own every Bluetooth/socket concern so the rest of the app
 * deals only with lines of text via [Listener]. Nothing here knows about JSON
 * or the application's features.
 *
 * Lifecycle (spec §5): publish an SDP record, [BluetoothServerSocket.accept]
 * one client, hand the caller a [LineChannel], then pump incoming lines until
 * the link drops — and loop back to accept the next connection.
 *
 * All blocking Bluetooth calls run on a single dedicated worker thread, because
 * `accept()` and stream reads block.
 */
class SppBluetoothServer(
    private val adapter: BluetoothAdapter,
    private val listener: Listener,
) {
    /** Events surfaced to the rest of the app. Callbacks run on the worker thread. */
    interface Listener {
        /** A client connected; [channel] is ready for reading/writing. */
        fun onConnected(channel: LineChannel)

        /** One full protocol line arrived. */
        fun onLineReceived(line: String)

        /** The current client disconnected; the server returns to accepting. */
        fun onDisconnected()

        /** A non-fatal error occurred (e.g. accept failed); server keeps running. */
        fun onError(message: String, cause: Throwable?)
    }

    private val sppUuid: UUID = UUID.fromString(Protocol.SPP_UUID)

    @Volatile private var running = false
    @Volatile private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var clientSocket: BluetoothSocket? = null
    private var worker: Thread? = null

    /** Start accepting connections. Idempotent. Requires BLUETOOTH_CONNECT (API 31+). */
    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        worker = Thread({ acceptLoop() }, "relayme-spp-server").also { it.start() }
    }

    /** Stop the server and close any open sockets. Safe to call repeatedly. */
    fun stop() {
        running = false
        closeQuietly(clientSocket)
        closeQuietly(serverSocket)
        clientSocket = null
        serverSocket = null
        worker = null
    }

    @SuppressLint("MissingPermission")
    private fun acceptLoop() {
        while (running) {
            val server = try {
                adapter.listenUsingRfcommWithServiceRecord(Protocol.SERVICE_NAME, sppUuid)
            } catch (e: IOException) {
                listener.onError("Could not open SPP server socket", e)
                return
            }
            serverSocket = server

            val socket = try {
                server.accept() // blocks until a client connects
            } catch (e: IOException) {
                if (running) listener.onError("accept() failed", e)
                closeQuietly(server)
                continue
            } finally {
                // Only one client at a time; stop advertising once connected.
                closeQuietly(server)
                serverSocket = null
            }

            serveClient(socket)
        }
    }

    private fun serveClient(socket: BluetoothSocket) {
        clientSocket = socket
        val channel = try {
            LineChannel(socket.inputStream, socket.outputStream)
        } catch (e: IOException) {
            listener.onError("Could not open socket streams", e)
            closeQuietly(socket)
            clientSocket = null
            return
        }

        listener.onConnected(channel)
        try {
            while (running) {
                val line = channel.readLine() ?: break
                if (line.isNotEmpty()) listener.onLineReceived(line)
            }
        } catch (e: IOException) {
            Log.d(TAG, "Read loop ended: ${e.message}")
        } finally {
            closeQuietly(socket)
            clientSocket = null
            listener.onDisconnected()
        }
    }

    private fun closeQuietly(closeable: AutoCloseable?) {
        try { closeable?.close() } catch (_: Exception) { /* best effort */ }
    }

    private companion object {
        const val TAG = "RelaySppServer"
    }
}
