package bluetooth;

/**
 * Connection callbacks, deliberately free of any {@code javax.bluetooth} types.
 *
 * Why a separate top-level interface (not a nested type inside {@link BtClient}):
 * the MIDlet implements this interface, and on a device <b>without</b> JSR-82 the
 * MIDlet must still load and run so it can report that Bluetooth is unavailable.
 * If the callback interface lived inside {@code BtClient}, loading the MIDlet
 * could transitively try to resolve {@code javax.bluetooth.*} and fail with
 * {@code NoClassDefFoundError}. Keeping it here means only {@code BtClient}
 * itself — instantiated lazily, after the JSR-82 check — touches the optional API.
 *
 * Callbacks are delivered on the client's reader thread, not the UI thread.
 */
public interface ConnectionListener {
    void onConnected();
    void onLineReceived(String line);
    void onDisconnected();
    void onError(String message);
}
