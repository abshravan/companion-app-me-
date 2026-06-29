package protocol;

import java.util.Hashtable;

/**
 * A parsed inbound message: a thin, typed view over the raw {@link Hashtable}
 * produced by {@link Json}.
 *
 * Keeping field-name knowledge here (and in {@link ProtocolCodec}) means the UI
 * and Bluetooth layers never touch JSON keys directly.
 */
public final class Message {

    private final Hashtable fields;

    public Message(Hashtable fields) {
        this.fields = fields;
    }

    /** The `type` of an event (e.g. "hello", "notification"), or null. */
    public String getType() {
        return getString("type");
    }

    /** The `cmd` of a command (rare on the client side), or null. */
    public String getCmd() {
        return getString("cmd");
    }

    /** @return the string value for {@code key}, or null if absent/not a string. */
    public String getString(String key) {
        Object value = fields.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    /** @return the string value for {@code key}, or {@code def} if absent. */
    public String getString(String key, String def) {
        String value = getString(key);
        return value != null ? value : def;
    }

    /**
     * @return the integer value for {@code key}, or {@code def} if absent.
     *
     * {@link Json} returns every number as a {@link Long} (no {@code Double},
     * which is absent on CLDC 1.0), so only that case is handled here.
     */
    public int getInt(String key, int def) {
        Object value = fields.get(key);
        if (value instanceof Long) {
            return (int) ((Long) value).longValue();
        }
        return def;
    }

    /** @return the boolean value for {@code key}, or {@code def} if absent. */
    public boolean getBoolean(String key, boolean def) {
        Object value = fields.get(key);
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        return def;
    }
}
