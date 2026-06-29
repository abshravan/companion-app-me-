package bluetooth;

import java.io.IOException;

import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;

/**
 * JSR-82 Bluetooth client: finds the Android RelayME server, connects to it
 * over RFCOMM, and pumps newline-delimited lines to a {@link Listener}.
 *
 * All Bluetooth concerns live here so the UI and protocol layers never touch
 * {@code javax.bluetooth} or streams.
 *
 * <p><b>Java ME limitation:</b> JSR-82 is an <i>optional</i> package. Callers
 * must check {@link #isBluetoothAvailable()} first; on a device without it,
 * none of these APIs exist at runtime.</p>
 *
 * <p><b>Discovery approach (Milestone 2):</b> {@link DiscoveryAgent#selectService}
 * performs an inquiry plus service search and returns a connection URL for the
 * first reachable device advertising the SPP UUID. It is the simplest reliable
 * path for a first handshake. Because many devices expose SPP, a later milestone
 * will add explicit device selection / bonding so the client always targets the
 * user's own phone.</p>
 */
public final class BtClient {

    /** Callbacks are delivered on the client's reader thread, not the UI thread. */
    public interface Listener {
        void onConnected();
        void onLineReceived(String line);
        void onDisconnected();
        void onError(String message);
    }

    /** Well-known Serial Port Profile UUID (16-bit 0x1101), per spec §6. */
    private static final UUID SPP_UUID = new UUID("1101", true);

    private final Listener listener;

    private Thread worker;
    private LineConnection link;
    private volatile boolean running;

    public BtClient(Listener listener) {
        this.listener = listener;
    }

    /** @return true if this device implements the optional JSR-82 package. */
    public static boolean isBluetoothAvailable() {
        return System.getProperty("bluetooth.api.version") != null;
    }

    /** Start discovery + connection on a background thread. Idempotent. */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread() {
            public void run() {
                connectAndPump();
            }
        };
        worker.start();
    }

    /** Stop the client and close the connection. Safe to call repeatedly. */
    public synchronized void stop() {
        running = false;
        LineConnection current = link;
        if (current != null) {
            current.close(); // unblocks the reader's blocking read()
        }
    }

    /** Send a protocol line to the server, if connected. */
    public void send(String line) {
        LineConnection current = link;
        if (current == null) {
            return;
        }
        try {
            current.writeLine(line);
        } catch (IOException e) {
            listener.onError("send failed: " + e.getMessage());
        }
    }

    // ---- worker thread ----

    private void connectAndPump() {
        String url = discoverServerUrl();
        if (url == null) {
            running = false;
            listener.onError("No RelayME server found");
            return;
        }

        LineConnection connection;
        try {
            StreamConnection stream = (StreamConnection) Connector.open(url);
            connection = new LineConnection(stream);
        } catch (IOException e) {
            running = false;
            listener.onError("Connect failed: " + e.getMessage());
            return;
        }

        link = connection;
        listener.onConnected();

        try {
            while (running) {
                String line = connection.readLine();
                if (line == null) {
                    break; // server closed the stream
                }
                if (line.length() > 0) {
                    listener.onLineReceived(line);
                }
            }
        } catch (IOException e) {
            // Expected when stop() closes the stream underneath us.
        } finally {
            connection.close();
            link = null;
            running = false;
            listener.onDisconnected();
        }
    }

    /** @return a {@code btspp://} URL for the server, or null if none found. */
    private String discoverServerUrl() {
        try {
            LocalDevice local = LocalDevice.getLocalDevice();
            DiscoveryAgent agent = local.getDiscoveryAgent();
            return agent.selectService(SPP_UUID, ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
        } catch (IOException e) {
            listener.onError("Discovery failed: " + e.getMessage());
            return null;
        } catch (SecurityException e) {
            listener.onError("Bluetooth permission denied");
            return null;
        }
    }
}
