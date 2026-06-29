# javame / protocol

Hand-written JSON parser/writer — the **only** place on the Java ME side that
knows the wire format.

**Why it exists:** CLDC 1.1 / MIDP 2.0 ships no JSON support and pulling in a
third-party library would cost precious heap and jar size. A small, purpose-built
parser that understands exactly the message shapes in the spec is far cheaper.

**Responsibilities**
- Parse one newline-delimited JSON line → a lightweight message object.
- Build command lines (e.g. `{"cmd":"music_next"}\n`) to send to Android.
- Apply the robustness rules: ignore unknown types/fields, drop malformed lines
  — see [`PROTOCOL.md`](../../../docs/protocol/PROTOCOL.md) §7.

**Design notes**
- Allocation-light: avoid building large intermediate object graphs.
- Flat by design: the wire format avoids nested objects/arrays precisely so this
  parser can stay simple.

**Contract:** must always match
[`docs/protocol/PROTOCOL.md`](../../../docs/protocol/PROTOCOL.md). Protocol
changes update the spec, this package, and the Android `protocol` module
together.
