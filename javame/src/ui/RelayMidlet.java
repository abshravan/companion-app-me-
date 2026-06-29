package ui;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

import bluetooth.BtClient;
import bluetooth.ConnectionListener;
import protocol.Message;
import protocol.ProtocolCodec;

/**
 * Application entry point and controller.
 *
 * It wires the three isolated layers together without letting them know about
 * one another: {@link BtClient} (transport), {@link ProtocolCodec} (wire format)
 * and {@link HomeScreen} (UI). This is small enough to act as the controller
 * directly; as more screens arrive in later milestones the wiring can move into
 * a dedicated controller without touching the layers.
 *
 * Milestone 2 behaviour: connect, wait for the server's `hello` (the Android
 * side greets first, spec §5), reply with our own `hello`, and show
 * "Hello Android".
 */
public final class RelayMidlet extends MIDlet
        implements HomeScreen.Controller, ConnectionListener {

    private Display display;
    private HomeScreen home;
    // Created lazily, only once JSR-82 is confirmed present (see onConnectRequested).
    // Constructing it earlier would load javax.bluetooth classes and crash with
    // NoClassDefFoundError on devices that lack the optional package.
    private BtClient client;

    protected void startApp() {
        if (display == null) {
            display = Display.getDisplay(this);
            home = new HomeScreen(this);
        }
        display.setCurrent(home.getDisplayable());

        if (bluetoothAvailable()) {
            home.setStatus("Ready. Press Connect to reach your phone.");
        } else {
            home.setStatus("This phone has no Java Bluetooth (JSR-82) support.");
        }
    }

    /**
     * @return true if the optional JSR-82 package is present. This only reads a
     *         system property — it must NOT touch {@link BtClient}, because doing
     *         so would load {@code javax.bluetooth} and defeat the whole point.
     */
    private boolean bluetoothAvailable() {
        return System.getProperty("bluetooth.api.version") != null;
    }

    protected void pauseApp() {
        // Nothing to release; the connection is intentionally kept alive.
    }

    protected void destroyApp(boolean unconditional) {
        if (client != null) {
            client.stop();
        }
    }

    // ---- HomeScreen.Controller (UI thread) ----

    public void onConnectRequested() {
        if (!bluetoothAvailable()) {
            home.setStatus("Bluetooth not available on this device.");
            return;
        }
        // First use of BtClient — safe now that JSR-82 is confirmed present.
        if (client == null) {
            client = new BtClient(this);
        }
        home.setStatus("Searching for RelayME server…");
        client.start();
    }

    public void onExitRequested() {
        destroyApp(true);
        notifyDestroyed();
    }

    // ---- BtClient.Listener (reader thread) ----

    public void onConnected() {
        home.setStatus("Connected. Waiting for handshake…");
    }

    public void onLineReceived(String line) {
        Message message = ProtocolCodec.decode(line);
        if (message == null) {
            return; // malformed line dropped (spec §7)
        }
        if (ProtocolCodec.isAndroidHello(message)) {
            // Spec §5: reply to the server's greeting with our own hello.
            client.send(ProtocolCodec.encodeHello(deviceName()));
            String name = message.getString("name", "Android");
            home.setStatus("Hello Android — connected to " + name);
        }
        // Other message types arrive in later milestones; ignored for now (§7).
    }

    public void onDisconnected() {
        home.setStatus("Disconnected. Press Connect to retry.");
    }

    public void onError(String message) {
        home.setStatus("Error: " + message);
    }

    /** Best-effort device name for the handshake; empty if unknown. */
    private String deviceName() {
        String name = System.getProperty("microedition.platform");
        return name != null ? name : "Java ME";
    }
}
