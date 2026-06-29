# RelayME

**Turn a Java ME phone into a companion device for Android.**

RelayME lets an old MIDP 2.0 / CLDC 1.1 feature phone act like a smartwatch for
a modern Android phone over Bluetooth. The feature phone can receive
notifications, show incoming calls, display battery information, control music
playback, and more — while staying lightweight and responsive.

> **Project status:** early development. See [Milestones](#milestones).

---

## How it works

```
   ┌─────────────────────────┐        Bluetooth RFCOMM / SPP        ┌──────────────────────────┐
   │     Android Companion    │  <-------- UTF-8 JSON lines ------->  │      Java ME Client       │
   │        (Kotlin)          │                                      │   (MIDP 2.0 / CLDC 1.1)   │
   │                          │                                      │                           │
   │  • Bluetooth SPP server  │   {"type":"notification",...}\n      │  • Bluetooth SPP client   │
   │  • Notification listener │   ───────────────────────────────►  │  • LCDUI screens          │
   │  • Media session control │                                      │  • RMS storage            │
   │  • Call / battery monitor│   ◄───────────────────────────────  │  • Auto-reconnect         │
   │  • Background service     │   {"cmd":"music_next"}\n             │                           │
   └─────────────────────────┘                                      └──────────────────────────┘
```

The **Android app owns all communication and intelligence**: it watches the
system, decides what is worth sending, and acts on commands. The **Java ME
client stays dumb and small**: it renders what it receives and sends simple
commands back. The line between them is the [wire protocol](docs/protocol/PROTOCOL.md).

---

## Repository layout

```
RelayME/
├── android/            Android Companion app (Kotlin)
│   ├── app/            Application module: UI, service wiring, manifest
│   ├── core/           Domain models & use-cases, framework-independent
│   ├── bluetooth/      Bluetooth Classic SPP server, isolated from the rest
│   ├── notifications/  NotificationListenerService integration
│   ├── media/          MediaSession controller & playback state
│   └── protocol/       JSON (de)serialisation of the wire protocol
├── javame/             Java ME Client (MIDP 2.0 / CLDC 1.1)
│   └── src/
│       ├── ui/         LCDUI screens (Home, Notifications, Calls, …)
│       ├── bluetooth/  JSR-82 client & connection management
│       ├── storage/    RMS-backed local storage & settings
│       └── protocol/   Hand-written, allocation-light JSON parser/writer
└── docs/
    └── protocol/       The shared wire-protocol specification
```

Each subfolder contains a `README.md` explaining why that module exists and what
it is responsible for. Modules are kept independent on purpose: Bluetooth logic,
protocol logic, UI, and storage never bleed into one another.

---

## The protocol

Communication is **UTF-8, newline-delimited JSON** over **Bluetooth RFCOMM /
SPP**. The full, versioned specification — framing, message catalogue,
versioning, and robustness rules — lives in
**[`docs/protocol/PROTOCOL.md`](docs/protocol/PROTOCOL.md)**.

> Never invent a parallel protocol. Always extend the existing one.

---

## Milestones

| #  | Goal                                                        | Status      |
|----|-------------------------------------------------------------|-------------|
| 1  | Repository setup & shared protocol documentation           | ✅ Done     |
| 2  | Bluetooth handshake: exchange `Hello Android` / `Hello Java`| ✅ Done     |
| 3  | Automatic reconnect                                         | ⏳ Next     |
| 4  | Notification bridge                                         | ⬜ Planned  |
| 5  | Incoming call screen                                        | ⬜ Planned  |
| 6  | Music controls                                             | ⬜ Planned  |
| 7  | Battery sync                                               | ⬜ Planned  |
| 8  | Settings                                                   | ⬜ Planned  |
| 9  | Plugin architecture                                        | ⬜ Planned  |
| 10 | Stable release                                            | ⬜ Planned  |

Development is **incremental**: each milestone must compile before the next
begins.

### Building

All commands below are written to run **from the repository root** (the
subshells keep your working directory there).

**Android** (`android/`) — a standard multi-module Gradle project. The build
targets JVM 17; any JDK 17 or newer (e.g. 21) can run it. With the Android SDK
installed:

```
( cd android && ./gradlew :protocol:test )      # pure-JVM codec tests (no SDK)
( cd android && ./gradlew :app:assembleDebug )  # build the APK (needs the SDK)
```

The `core` and `protocol` modules are pure-JVM Kotlin libraries, so the
protocol codec is unit-tested without an emulator.

**Java ME** (`javame/`) — built with Gradle, **no Wireless Toolkit required**.
The CLDC/MIDP/JSR-82 API stubs and the preverifier all come from Maven Central,
so it runs on a normal JDK 17+:

```
( cd javame && ./gradlew assemble )   # -> build/dist/RelayME.jar + RelayME.jad
```

(See `javame/README.md` for how the WTK-free build works, and the legacy
Antenna + WTK alternative.)

The `protocol` package is CLDC-safe plain Java and can be compiled and tested
with a normal JDK:

```
( cd javame && javac -d build/test src/protocol/*.java test/protocol/*.java \
    && java -cp build/test protocol.ProtocolTest )
```

---

## Platform notes & limitations

* **Java ME Bluetooth (JSR-82)** is an *optional* package. A target device must
  expose `bluetooth.api.version` for the client to run.
* **Answering calls** programmatically on Android requires `ANSWER_PHONE_CALLS`
  (API 26+) and is intentionally out of scope for v1; only reject/silence are
  supported.
* **Notification access** on Android requires the user to grant
  `NotificationListenerService` permission manually in system settings.
* The Java ME client requires **no internet access**.

---

## License

See [`LICENSE`](LICENSE).
