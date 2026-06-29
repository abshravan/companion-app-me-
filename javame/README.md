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

## Build (recommended: Gradle, no Wireless Toolkit)

Java ME needs three steps a desktop JDK cannot do alone: compile against the
CLDC 1.1 / MIDP 2.0 / JSR-82 APIs, *preverify*, then package into a JAR + JAD.
The classic Sun WTK + Antenna toolchain drags in 32-bit native binaries and an
ancient JDK that no longer install cleanly on modern Linux.

`build.gradle.kts` avoids all of that — every dependency comes from Maven
Central and it runs on a normal JDK 17+:

```
./gradlew assemble        # -> build/dist/RelayME.jar + RelayME.jad
```

How it works:

| Step | Tool (from Maven Central) |
|------|---------------------------|
| Compile against CLDC/MIDP | `org.microemu:microemu-cldc`, `microemu-midp` |
| Compile against JSR-82 (`javax.bluetooth`) | `net.sf.bluecove:bluecove` |
| **Preverify** (adds the CLDC StackMap) | `net.sf.proguard:proguard-base` in `-microedition` mode |
| Downgrade class version to 48 (Java 1.4) | ProGuard `-target 1.4`, for old KVMs |

The stub jars are the device's own platform; they are **not** bundled — only our
own classes ship. Copy `build/dist/RelayME.jad` + `RelayME.jar` to the phone to
install.

### Diagnostic probe

`assemble` also builds a standalone **`RelayME-Probe.jar`** (or run `./gradlew
probe`). It contains a single MIDlet that references only `java.lang` + LCDUI —
no `javax.bluetooth` — so it installs and runs on *any* MIDP 2.0 device and
prints the device's CLDC/MIDP version and whether JSR-82 is present.

Use it when the full app fails to start (e.g. `NoClassDefFoundError`): if the
probe shows `bluetooth.api: (none)`, the device lacks the JSR-82 Bluetooth API
and cannot be a RelayME companion. The same MIDlet is also available as
"RelayME Probe" (MIDlet-2) inside the main suite.

### Legacy alternative: Antenna + WTK

`build.xml` still drives the traditional [Antenna](https://antenna.sourceforge.net)
+ Wireless Toolkit route for anyone who already has a WTK installed:

```
ant -Dwtk.home=/path/to/WTK -Dantenna.jar=/path/to/antenna-bin.jar
```

### Testing the protocol on a desktop JDK

The `protocol` package uses only CLDC-safe APIs (no `javax.microedition`), so it
compiles and runs on a normal JDK for fast feedback:

```
javac -d build/test src/protocol/*.java test/protocol/*.java
java -cp build/test protocol.ProtocolTest
```

The `bluetooth` and `ui` packages depend on JSR-82 / LCDUI and only build inside
the WTK.
