package com.relayme.core

/**
 * The handshake message exchanged by both sides immediately after the
 * Bluetooth link opens (see `docs/protocol/PROTOCOL.md` §8.1).
 *
 * This is the only message defined in Milestone 2. Later milestones add their
 * own domain types alongside it; this one is never widened into a god-object.
 *
 * @param role    which peer sent it
 * @param version protocol version reported by the sender
 * @param name    human-readable device name (may be empty)
 */
data class Hello(
    val role: Role,
    val version: Int = Protocol.VERSION,
    val name: String = "",
)
