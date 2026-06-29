package com.relayme.app

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The single source of truth for the link's status, observed by the UI and
 * updated by the background service.
 *
 * Kept as a tiny hand-rolled observable (no LiveData/Flow dependency) to honour
 * the "avoid unnecessary libraries" goal. Updates are delivered on the main
 * thread so the UI can consume them directly.
 */
object RelayLinkState {

    enum class Phase { STOPPED, LISTENING, CONNECTED }

    data class Snapshot(val phase: Phase, val message: String)

    fun interface Listener {
        fun onState(snapshot: Snapshot)
    }

    @Volatile
    var current: Snapshot = Snapshot(Phase.STOPPED, "Idle")
        private set

    private val listeners = CopyOnWriteArraySet<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Update the state and notify listeners on the main thread. */
    fun update(phase: Phase, message: String) {
        val snapshot = Snapshot(phase, message)
        current = snapshot
        mainHandler.post { listeners.forEach { it.onState(snapshot) } }
    }

    /** Register a listener and immediately deliver the current snapshot. */
    fun addListener(listener: Listener) {
        listeners.add(listener)
        val snapshot = current
        mainHandler.post { listener.onState(snapshot) }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }
}
