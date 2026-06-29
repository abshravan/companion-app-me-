package com.relayme.protocol

import com.relayme.core.Hello
import com.relayme.core.Protocol
import com.relayme.core.Role

/**
 * Translates between domain objects and protocol lines. This is the only place
 * in the Android app that knows the JSON field names from the spec, so the
 * wire format never leaks into Bluetooth or domain code.
 *
 * Decoding follows the spec's robustness rules (§7): malformed lines and
 * messages missing required fields yield a non-fatal result, never an
 * exception.
 */
object ProtocolCodec {

    /** Result of decoding one inbound line. */
    sealed interface Inbound {
        /** A recognised, well-formed `hello` handshake. */
        data class HelloReceived(val hello: Hello) : Inbound

        /**
         * A syntactically valid message we do not handle in this milestone
         * (unknown `type`/`cmd`). Kept rather than dropped so callers can log
         * it; higher layers simply ignore it (spec §7 rule 1).
         */
        data class Unhandled(val type: String?, val cmd: String?) : Inbound
    }

    /** Build the outbound `hello` line (without the trailing newline). */
    fun encodeHello(hello: Hello): String = Json.writeObject(
        linkedMapOf(
            "type" to "hello",
            "role" to hello.role.wire,
            "v" to hello.version,
            "name" to hello.name,
        )
    )

    /**
     * Decode one line into an [Inbound], or `null` if the line is not a usable
     * JSON object (malformed / over-length / not an object). A `null` here maps
     * to spec §7 rule 5 ("discard the line, resume").
     */
    fun decode(line: String): Inbound? {
        val obj = Json.parseObjectOrNull(line) ?: return null
        val type = obj["type"] as? String
        val cmd = obj["cmd"] as? String

        if (type == "hello") {
            val hello = decodeHello(obj) ?: return null
            return Inbound.HelloReceived(hello)
        }
        return Inbound.Unhandled(type, cmd)
    }

    /** @return a [Hello] or `null` if a required field is missing/invalid (§7 rule 4). */
    private fun decodeHello(obj: Map<String, Any?>): Hello? {
        val role = Role.fromWire(obj["role"] as? String) ?: return null
        val version = (obj["v"] as? Double)?.toInt() ?: Protocol.VERSION
        val name = obj["name"] as? String ?: ""
        return Hello(role = role, version = version, name = name)
    }
}
