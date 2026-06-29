package com.relayme.bluetooth

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * A newline-delimited, UTF-8 framing layer over a pair of raw streams.
 *
 * Why it exists: RFCOMM is a byte stream and does not preserve message
 * boundaries, so the protocol terminates every message with `\n` (spec §2).
 * This class is the single place that turns that byte stream into lines and
 * back, keeping framing out of both the server and the protocol codec.
 *
 * It knows nothing about JSON — it moves lines of text.
 *
 * @param maxLineLength lines longer than this are discarded (spec §7 rule 5).
 */
class LineChannel(
    private val input: InputStream,
    private val output: OutputStream,
    private val maxLineLength: Int = 4096,
) {
    private val readBuffer = StringBuilder(128)

    /** Append a newline and write the line as UTF-8, flushing immediately. */
    @Throws(IOException::class)
    fun writeLine(line: String) {
        val bytes = (line + "\n").toByteArray(StandardCharsets.UTF_8)
        synchronized(output) {
            output.write(bytes)
            output.flush()
        }
    }

    /**
     * Block until a full line is read.
     *
     * @return the line without its terminator, or `null` at end of stream.
     *         Over-length lines are dropped and reading resumes at the next
     *         newline, so a misbehaving peer cannot exhaust memory.
     */
    @Throws(IOException::class)
    fun readLine(): String? {
        readBuffer.setLength(0)
        var overflowed = false
        while (true) {
            val b = input.read()
            if (b == -1) return if (readBuffer.isEmpty() && !overflowed) null else readBuffer.toString()
            val c = b.toChar()
            when {
                c == '\n' -> {
                    if (overflowed) { readBuffer.setLength(0); overflowed = false; continue }
                    return readBuffer.toString()
                }
                c == '\r' -> continue // tolerate CR in framing (spec §2)
                readBuffer.length >= maxLineLength -> overflowed = true
                else -> readBuffer.append(c)
            }
        }
    }
}
