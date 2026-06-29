# javame / bluetooth

JSR-82 Bluetooth **client**, isolated from UI and storage.

**Why it exists:** to keep all `javax.bluetooth` and stream handling in one
place, including connection management and (from Milestone 3) automatic
reconnection.

**Responsibilities**
- Discover the Android SPP service by UUID `0x1101` (see the
  [protocol spec](../../../docs/protocol/PROTOCOL.md) §6).
- Open an RFCOMM `StreamConnection` and provide a line-oriented read/write
  channel.
- Manage the connection lifecycle and reconnection.

**Key JSR-82 APIs (introduced in Milestone 2)**
- `javax.bluetooth.LocalDevice` / `DiscoveryAgent` — discover the service.
- `javax.microedition.io.Connector.open("btspp://…")` — open the RFCOMM client
  connection.
- `StreamConnection.openInputStream()/openOutputStream()` — the byte channel.

**Java ME limitation:** JSR-82 is optional; the client verifies
`System.getProperty("bluetooth.api.version")` before use.

**Does not contain** JSON or UI code.
