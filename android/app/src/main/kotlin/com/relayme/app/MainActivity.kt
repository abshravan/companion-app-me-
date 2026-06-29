package com.relayme.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.relayme.app.databinding.ActivityMainBinding

/**
 * The single screen of the Android companion. Its only jobs are to request the
 * runtime permissions the link needs and to start/stop the [RelayService],
 * reflecting [RelayLinkState] back to the user.
 *
 * All real work happens in the service; the activity stays thin so it can be
 * closed without dropping the Bluetooth connection.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val stateListener = RelayLinkState.Listener { snapshot ->
        binding.statusText.text = snapshot.message
        val running = snapshot.phase != RelayLinkState.Phase.STOPPED
        binding.startButton.isEnabled = !running
        binding.stopButton.isEnabled = running
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                RelayService.start(this)
            } else {
                binding.statusText.text = getString(R.string.status_permission_denied)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startButton.setOnClickListener { ensurePermissionsThenStart() }
        binding.stopButton.setOnClickListener { RelayService.stop(this) }
    }

    override fun onStart() {
        super.onStart()
        RelayLinkState.addListener(stateListener)
    }

    override fun onStop() {
        RelayLinkState.removeListener(stateListener)
        super.onStop()
    }

    private fun ensurePermissionsThenStart() {
        val needed = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) RelayService.start(this) else permissionLauncher.launch(needed.toTypedArray())
    }

    /** Permissions differ by OS version: BLUETOOTH_CONNECT on 31+, notifications on 33+. */
    private fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
