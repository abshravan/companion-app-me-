# android / bluetooth

Bluetooth Classic (RFCOMM / SPP) **server**, fully isolated.

**Why it exists:** to keep all socket and transport concerns in one place so the
rest of the app never touches `BluetoothAdapter`, `BluetoothServerSocket`, or
raw streams.

**Responsibilities**
- Advertise the SPP service record (UUID `0x1101`, name `RelayME` — see the
  [protocol spec](../../docs/protocol/PROTOCOL.md) §6).
- Accept an incoming RFCOMM connection from the Java ME client.
- Provide a line-oriented read/write channel (newline-delimited frames).
- Surface connect / disconnect events to the rest of the app.

**Key Android APIs (introduced in Milestone 2)**
- `BluetoothAdapter.listenUsingRfcommWithServiceRecord(name, uuid)` — opens a
  listening SPP socket and publishes its SDP record.
- `BluetoothServerSocket.accept()` — blocks until the client connects.
- `BluetoothSocket.getInputStream()/getOutputStream()` — the byte channel.

**Does not contain** any JSON or protocol knowledge — it moves lines of bytes.
