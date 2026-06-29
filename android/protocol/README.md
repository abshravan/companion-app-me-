# android / protocol

The **only** place in the Android app that knows the JSON wire format.

**Why it exists:** to keep serialisation in one module so the protocol can
evolve without touching feature code, and so the wire format never leaks into
Bluetooth or domain layers.

**Responsibilities**
- Serialise `core` domain objects → newline-delimited JSON events.
- Parse incoming JSON command lines → `core` command objects.
- Enforce the robustness rules from the spec (ignore unknown types/fields,
  drop malformed lines) — see [`PROTOCOL.md`](../../docs/protocol/PROTOCOL.md) §7.

**Contract:** this module must always match
[`docs/protocol/PROTOCOL.md`](../../docs/protocol/PROTOCOL.md). Any protocol
change updates the spec, this module, and the Java ME `protocol` package
together.
