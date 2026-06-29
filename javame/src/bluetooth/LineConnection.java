package bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.StreamConnection;

/**
 * Newline-delimited, UTF-8 framing over a JSR-82 {@link StreamConnection}.
 *
 * Why it exists: RFCOMM is a byte stream with no message boundaries, so the
 * protocol terminates every message with '\n' (spec §2). This class is the one
 * place that turns the stream into lines and back, keeping framing out of the
 * client logic. It reads whole UTF-8 lines (not byte-by-byte chars) so
 * multi-byte characters survive intact.
 *
 * It knows nothing about JSON.
 */
public final class LineConnection {

    private static final int MAX_LINE = 4096;

    private final StreamConnection connection;
    private final InputStream in;
    private final OutputStream out;

    private byte[] buffer = new byte[128];

    public LineConnection(StreamConnection connection) throws IOException {
        this.connection = connection;
        this.in = connection.openInputStream();
        this.out = connection.openOutputStream();
    }

    /** Write the line as UTF-8 followed by '\n', flushing immediately. */
    public synchronized void writeLine(String line) throws IOException {
        byte[] bytes = line.getBytes("UTF-8");
        out.write(bytes);
        out.write('\n');
        out.flush();
    }

    /**
     * Block until a full line is read.
     *
     * @return the line (decoded as UTF-8, without its terminator), or null at
     *         end of stream. Over-length lines are discarded and reading
     *         resumes at the next newline (spec §7 rule 5), so a misbehaving
     *         server cannot exhaust the tiny heap.
     */
    public String readLine() throws IOException {
        int len = 0;
        boolean overflowed = false;
        while (true) {
            int b = in.read();
            if (b == -1) {
                if (len == 0 && !overflowed) {
                    return null;
                }
                return new String(buffer, 0, len, "UTF-8");
            }
            if (b == '\n') {
                if (overflowed) {
                    len = 0;
                    overflowed = false;
                    continue;
                }
                return new String(buffer, 0, len, "UTF-8");
            }
            if (b == '\r') {
                continue; // tolerate CR in framing (spec §2)
            }
            if (len >= MAX_LINE) {
                overflowed = true;
                continue;
            }
            if (len >= buffer.length) {
                grow();
            }
            buffer[len++] = (byte) b;
        }
    }

    /** Double the read buffer, capped at MAX_LINE. */
    private void grow() {
        int newSize = buffer.length * 2;
        if (newSize > MAX_LINE) {
            newSize = MAX_LINE;
        }
        byte[] bigger = new byte[newSize];
        System.arraycopy(buffer, 0, bigger, 0, buffer.length);
        buffer = bigger;
    }

    /** Close the streams and the underlying connection, ignoring errors. */
    public void close() {
        try { in.close(); } catch (IOException ignored) { }
        try { out.close(); } catch (IOException ignored) { }
        try { connection.close(); } catch (IOException ignored) { }
    }
}
