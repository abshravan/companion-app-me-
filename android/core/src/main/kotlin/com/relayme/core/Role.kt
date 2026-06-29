package com.relayme.core

/**
 * Identifies which side of the link sent a message.
 *
 * Used in the `hello` handshake (`role` field). Kept as a small enum rather
 * than raw strings so the rest of the code never compares magic strings.
 */
enum class Role(val wire: String) {
    ANDROID("android"),
    JAVAME("javame");

    companion object {
        /** Parse a wire value, or `null` if it is unknown (robustness rule §7). */
        fun fromWire(value: String?): Role? = entries.firstOrNull { it.wire == value }
    }
}
