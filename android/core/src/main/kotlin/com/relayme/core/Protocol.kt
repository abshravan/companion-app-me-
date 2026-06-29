package com.relayme.core

/**
 * Protocol-wide constants shared across modules.
 *
 * Kept in [core] (the framework-independent layer) because the protocol
 * version is a domain fact, not an Android or Bluetooth detail. See
 * `docs/protocol/PROTOCOL.md` for the authoritative specification.
 */
object Protocol {
    /** Current wire-protocol version, exchanged in the `hello` handshake. */
    const val VERSION: Int = 1

    /** Well-known Serial Port Profile UUID (16-bit `0x1101`). */
    const val SPP_UUID: String = "00001101-0000-1000-8000-00805F9B34FB"

    /** SDP service record name advertised by the Android server. */
    const val SERVICE_NAME: String = "RelayME"
}
