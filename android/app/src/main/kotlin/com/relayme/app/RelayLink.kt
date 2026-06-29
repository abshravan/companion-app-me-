package com.relayme.app

import android.bluetooth.BluetoothAdapter
import android.util.Log
import com.relayme.bluetooth.LineChannel
import com.relayme.bluetooth.SppBluetoothServer
import com.relayme.core.Hello
import com.relayme.core.Protocol
import com.relayme.core.Role
import com.relayme.protocol.ProtocolCodec
import java.io.IOException

/**
 * Orchestrates one end of the RelayME link on Android.
 *
 * It connects the three isolated layers without letting them know about each
 * other: the [SppBluetoothServer] (transport), the [ProtocolCodec] (wire
 * format), and [RelayLinkState] (UI status). This is the Milestone 2 behaviour:
 * on connect it greets with `hello(android)` and reacts to the client's
 * `hello(javame)` — the "Hello Android" / "Hello Java" exchange.
 */
class RelayLink(
    adapter: BluetoothAdapter,
    private val deviceName: String,
) : SppBluetoothServer.Listener {

    private val server = SppBluetoothServer(adapter, this)

    @Volatile
    private var channel: LineChannel? = null

    fun start() {
        RelayLinkState.update(RelayLinkState.Phase.LISTENING, "Waiting for Java ME client…")
        server.start()
    }

    fun stop() {
        server.stop()
        channel = null
        RelayLinkState.update(RelayLinkState.Phase.STOPPED, "Stopped")
    }

    // --- SppBluetoothServer.Listener (called on the server's worker thread) ---

    override fun onConnected(channel: LineChannel) {
        this.channel = channel
        RelayLinkState.update(RelayLinkState.Phase.CONNECTED, "Client connected — handshaking…")
        // Spec §5: the Android side sends hello first.
        sendHello(channel)
    }

    override fun onLineReceived(line: String) {
        when (val inbound = ProtocolCodec.decode(line)) {
            is ProtocolCodec.Inbound.HelloReceived -> {
                val peer = inbound.hello
                if (peer.role == Role.JAVAME) {
                    val who = peer.name.ifEmpty { "Java ME client" }
                    RelayLinkState.update(
                        RelayLinkState.Phase.CONNECTED,
                        "Hello Java — connected to $who (protocol v${peer.version})",
                    )
                }
            }
            is ProtocolCodec.Inbound.Unhandled ->
                Log.d(TAG, "Ignoring unhandled message type=${inbound.type} cmd=${inbound.cmd}")
            null -> Log.d(TAG, "Dropped malformed line")
        }
    }

    override fun onDisconnected() {
        channel = null
        RelayLinkState.update(RelayLinkState.Phase.LISTENING, "Client disconnected, waiting…")
    }

    override fun onError(message: String, cause: Throwable?) {
        Log.w(TAG, "Bluetooth error: $message", cause)
        RelayLinkState.update(RelayLinkState.Phase.LISTENING, "Error: $message")
    }

    private fun sendHello(channel: LineChannel) {
        val line = ProtocolCodec.encodeHello(Hello(Role.ANDROID, Protocol.VERSION, deviceName))
        try {
            channel.writeLine(line)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to send hello", e)
        }
    }

    private companion object {
        const val TAG = "RelayLink"
    }
}
