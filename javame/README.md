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

Java ME needs three steps a desktop JDK cannot do alone: compile against the
CLDC 1.1 / MIDP 2.0 / JSR-82 stubs, *preverify*, then package into a JAR + JAD.
`build.xml` drives this with [Antenna](https://antenna.sourceforge.net) and a
Wireless Toolkit (Sun WTK 2.5.2 or the Nokia Series 40 SDK):

```
ant -Dwtk.home=/path/to/WTK -Dantenna.jar=/path/to/antenna-bin.jar
```

The output (`dist/RelayME.jad` + `RelayME.jar`) is installed onto the phone.

### Testing the protocol on a desktop JDK

The `protocol` package uses only CLDC-safe APIs (no `javax.microedition`), so it
compiles and runs on a normal JDK for fast feedback:

```
javac -d build/test src/protocol/*.java test/protocol/*.java
java -cp build/test protocol.ProtocolTest
```

The `bluetooth` and `ui` packages depend on JSR-82 / LCDUI and only build inside
the WTK.
