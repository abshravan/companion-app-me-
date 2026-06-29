# Java ME Client

The companion application that runs on the feature phone (MIDP 2.0 / CLDC 1.1).
It is intentionally **small and dumb**: it renders what Android sends and sends
back simple commands. All intelligence lives on the Android side.

## Design constraints

Java ME is a constrained environment, and every decision here is shaped by it:

- **No JSON library.** Parsing is done by a hand-written, allocation-light
  parser in `protocol/`.
- **Small heap & slow CPU.** Avoid object churn, avoid reflection, reuse
  buffers, keep screens lightweight.
- **JSR-82 is optional.** The client checks `bluetooth.api.version` before using
  any `javax.bluetooth` API.
- **LCDUI only** unless a Java ME-compatible alternative is clearly justified.

## Package map

```
javame/src/
├── ui/         LCDUI screens: Home, Notifications, Calls, Music, Battery, Settings
├── bluetooth/  JSR-82 client: discovery, connection, read/write, reconnect
├── storage/    RMS-backed notification storage and settings
└── protocol/   Hand-written JSON parser/writer matching the shared spec
```

Concerns are kept apart: Bluetooth code knows nothing about screens, the parser
knows nothing about RMS, and the UI knows nothing about sockets.

## Build

A build setup (MIDlet packaging into `.jad`/`.jar`) will be added in
**Milestone 2**, alongside the first Bluetooth handshake code. This milestone
establishes structure and the shared protocol only.
