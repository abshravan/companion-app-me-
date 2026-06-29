package protocol;

import java.util.Hashtable;

/**
 * The only place on the Java ME side that knows the JSON wire format. Mirrors
 * the Android {@code protocol} module so both ends stay in lock-step with
 * docs/protocol/PROTOCOL.md.
 */
public final class ProtocolCodec {

    /** Current wire-protocol version, exchanged in the handshake. */
    public static final int VERSION = 1;

    /** Role string this client reports in its `hello`. */
    public static final String ROLE_JAVAME = "javame";
    /** Role string the Android server reports. */
    public static final String ROLE_ANDROID = "android";

    private ProtocolCodec() {
    }

    /**
     * Build the client's `hello` line (without the trailing newline).
     *
     * @param name human-readable device name (may be empty)
     */
    public static String encodeHello(String name) {
        StringBuffer sb = new StringBuffer(48);
        sb.append("{\"type\":\"hello\",\"role\":\"");
        sb.append(ROLE_JAVAME);
        sb.append("\",\"v\":");
        sb.append(VERSION);
        sb.append(",\"name\":");
        Json.appendEscaped(sb, name == null ? "" : name);
        sb.append('}');
        return sb.toString();
    }

    /**
     * Decode one inbound line.
     *
     * @return a {@link Message}, or null if the line is not a usable JSON object
     *         (spec §7: drop malformed lines).
     */
    public static Message decode(String line) {
        Hashtable fields = Json.parseObject(line);
        if (fields == null) {
            return null;
        }
        return new Message(fields);
    }

    /** @return true if {@code message} is a `hello` sent by the Android server. */
    public static boolean isAndroidHello(Message message) {
        if (message == null) {
            return false;
        }
        return "hello".equals(message.getType())
                && ROLE_ANDROID.equals(message.getString("role"));
    }
}
