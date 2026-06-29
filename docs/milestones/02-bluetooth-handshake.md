# Milestone 2 — Bluetooth handshake

**Goal:** the two apps connect over Bluetooth SPP and exchange the handshake —
"Hello Android" from the phone, "Hello Java" from the feature phone.

## What was built

### Android (`android/`)
A multi-module Gradle project, dependencies pointing inward (Clean Architecture):

- **`core`** (pure JVM) — domain: `Hello`, `Role`, `Protocol` constants.
- **`protocol`** (pure JVM) — `Json` (hand-rolled, dependency-free codec) and
  `ProtocolCodec` (domain ↔ wire). Unit-tested on the JVM (12 tests).
- **`bluetooth`** (Android lib) — `SppBluetoothServer` + `LineChannel`: opens an
  SPP server socket, accepts a client, and exposes newline-delimited frames.
- **`app`** (Android app) — `RelayService` (persistent foreground service hosting
  the server), `RelayLink` (wires transport ↔ codec ↔ UI state), `MainActivity`
  (permissions + start/stop + status).

### Java ME (`javame/`)
Four isolated packages matching the documented layout:

- **`protocol`** — `Json`, `Message`, `ProtocolCodec`. CLDC-safe plain Java,
  tested on a desktop JDK (26 checks).
- **`bluetooth`** — `BtClient` (JSR-82 discovery + connect + reader thread) and
  `LineConnection` (UTF-8 line framing).
- **`ui`** — `RelayMidlet` (entry point + controller) and `HomeScreen` (LCDUI).

## Handshake flow (per protocol spec §5)

1. Android `RelayService` starts the SPP server and advertises UUID `0x1101`.
2. The Java ME client discovers the service and opens an RFCOMM connection.
3. **Android greets first**: `{"type":"hello","role":"android",...}`.
4. The client replies: `{"type":"hello","role":"javame",...}`.
5. Both screens show the peer ("Hello Android" / "Hello Java").

## Architectural decisions

- **Hand-written JSON on both sides.** Avoids libraries (jar size / heap on
  Java ME) and keeps the two implementations symmetric. Both follow the spec's
  robustness rules: malformed lines and unknown types are non-fatal.
- **Transport, protocol, and UI are separate everywhere.** `LineChannel` /
  `LineConnection` know bytes; `ProtocolCodec` knows JSON; the UI knows neither.
- **Foreground service on Android.** A companion link must survive the screen
  turning off, like a smartwatch — so the server lives in a foreground service,
  not an activity.

## Java ME / platform limitations

- **JSR-82 is optional.** `BtClient.isBluetoothAvailable()` checks
  `bluetooth.api.version`; the UI degrades gracefully if absent.
- **Discovery uses `DiscoveryAgent.selectService`** — the simplest reliable path
  for a first handshake, but it connects to the first reachable SPP device.
  Explicit device selection / bonding (so the client always targets the user's
  own phone) is deferred to a later milestone.
- **Answering calls** and other optional behaviours remain out of scope (spec §9.3).

## Verification

- Android protocol codec: `./gradlew :protocol:test` → 12 tests pass.
- Java ME protocol: `javac`/`java` on `protocol` + `test/protocol` → 26 checks pass.
- **Full Java ME app** (UI + JSR-82 Bluetooth + protocol): `./gradlew assemble`
  compiles, preverifies (ProGuard `-microedition`), and packages a class-version-48
  `RelayME.jar` + `RelayME.jad` — all from Maven Central, no Wireless Toolkit.
- The Android Bluetooth/UI/service layers depend on the Android SDK and are built
  with `:app:assembleDebug` once the SDK is installed.

> Note: the Java ME build uses Maven-hosted CLDC/MIDP/JSR-82 stubs (`microemu`,
> `bluecove`) instead of the legacy WTK, so it runs on a modern JDK 17+. See
> `javame/README.md`.
