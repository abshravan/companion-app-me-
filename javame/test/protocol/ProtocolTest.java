package protocol;

import java.util.Hashtable;

/**
 * Standalone test for the Java ME protocol package. Uses only plain Java so it
 * can be compiled and run with a normal JDK (the parser itself is CLDC-safe and
 * pulls in no javax.microedition classes). Run via the JDK, not on a device.
 *
 * This guards the trickiest, most portable part of the client — the JSON
 * codec — exactly as the Android side is guarded by its JUnit tests.
 */
public final class ProtocolTest {

    private static int checks = 0;

    public static void main(String[] args) {
        encodesHelloLine();
        decodesAndroidHello();
        helloRoundTrips();
        ignoresUnknownFields();
        defaultsForMissingFields();
        dropsMalformedLines();
        nonObjectsRejected();
        escapesAndUnescapes();
        parsesNumbersAndBooleans();
        rejectsNonAndroidHello();

        System.out.println("OK - " + checks + " checks passed");
    }

    private static void encodesHelloLine() {
        String line = ProtocolCodec.encodeHello("Nokia 6300");
        expectEquals("{\"type\":\"hello\",\"role\":\"javame\",\"v\":1,\"name\":\"Nokia 6300\"}", line);
        expect(line.indexOf('\n') < 0, "framing must not contain raw newline");
    }

    private static void decodesAndroidHello() {
        Message m = ProtocolCodec.decode("{\"type\":\"hello\",\"role\":\"android\",\"v\":1,\"name\":\"Pixel 7\"}");
        expect(m != null, "decode hello");
        expect(ProtocolCodec.isAndroidHello(m), "isAndroidHello");
        expectEquals("Pixel 7", m.getString("name"));
        expectEquals(1, m.getInt("v", -1));
    }

    private static void helloRoundTrips() {
        String line = ProtocolCodec.encodeHello("Nokia 6300");
        Message m = ProtocolCodec.decode(line);
        expect(m != null, "round-trip decode");
        expectEquals("hello", m.getType());
        expectEquals("javame", m.getString("role"));
        expectEquals("Nokia 6300", m.getString("name"));
    }

    private static void ignoresUnknownFields() {
        Message m = ProtocolCodec.decode("{\"type\":\"hello\",\"role\":\"android\",\"future\":\"x\",\"v\":1}");
        expect(ProtocolCodec.isAndroidHello(m), "unknown field tolerated");
    }

    private static void defaultsForMissingFields() {
        Message m = ProtocolCodec.decode("{\"type\":\"hello\",\"role\":\"android\"}");
        expectEquals("", m.getString("name", ""));
        expectEquals(1, m.getInt("v", 1));
    }

    private static void dropsMalformedLines() {
        expect(ProtocolCodec.decode("{\"type\":\"hello\",,,}") == null, "malformed -> null");
        expect(ProtocolCodec.decode("not json") == null, "garbage -> null");
        expect(ProtocolCodec.decode("") == null, "empty -> null");
        expect(ProtocolCodec.decode(null) == null, "null -> null");
    }

    private static void nonObjectsRejected() {
        expect(ProtocolCodec.decode("[\"array\"]") == null, "array -> null");
        expect(ProtocolCodec.decode("\"bare string\"") == null, "string -> null");
    }

    private static void escapesAndUnescapes() {
        String tricky = "Line\"quote\\slash\ttab\nnewline";
        StringBuffer sb = new StringBuffer();
        Json.appendEscaped(sb, tricky);
        Hashtable obj = Json.parseObject("{\"text\":" + sb.toString() + "}");
        expect(obj != null, "escaped object parses");
        expectEquals(tricky, (String) obj.get("text"));
    }

    private static void parsesNumbersAndBooleans() {
        Message m = ProtocolCodec.decode("{\"type\":\"battery\",\"level\":82,\"charging\":true,\"ratio\":0.5}");
        expectEquals(82, m.getInt("level", -1));
        expect(m.getBoolean("charging", false), "boolean true");
        expect(m.getInt("ratio", -7) == 0, "fractional truncates to int 0");
    }

    private static void rejectsNonAndroidHello() {
        Message own = ProtocolCodec.decode(ProtocolCodec.encodeHello("self"));
        expect(!ProtocolCodec.isAndroidHello(own), "own hello is not android hello");
        Message battery = ProtocolCodec.decode("{\"type\":\"battery\",\"level\":50}");
        expect(!ProtocolCodec.isAndroidHello(battery), "battery is not hello");
    }

    // ---- minimal assertion helpers ----

    private static void expect(boolean condition, String what) {
        checks++;
        if (!condition) {
            throw new RuntimeException("FAILED: " + what);
        }
    }

    private static void expectEquals(String expected, String actual) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new RuntimeException("FAILED: expected [" + expected + "] but was [" + actual + "]");
        }
    }

    private static void expectEquals(int expected, int actual) {
        checks++;
        if (expected != actual) {
            throw new RuntimeException("FAILED: expected [" + expected + "] but was [" + actual + "]");
        }
    }
}
