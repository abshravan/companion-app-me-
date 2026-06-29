# Android Companion

The Kotlin application that drives RelayME. It owns all communication and
intelligence: it observes the phone (notifications, calls, media, battery),
decides what to forward, runs a persistent Bluetooth server, and acts on
commands from the Java ME client.

## Module map

The app is split into independent modules so that no single concern can grow
into a monolith. Dependencies point **inward** toward `core`; `core` depends on
nothing Android-specific.

| Module          | Responsibility                                                        |
|-----------------|-----------------------------------------------------------------------|
| `app`           | Android entry points: foreground service, UI, pairing, manifest wiring.|
| `core`          | Framework-independent domain models and use-cases.                    |
| `bluetooth`     | Bluetooth Classic SPP server, sockets, read/write loop. Nothing else. |
| `notifications` | `NotificationListenerService` integration → domain notifications.     |
| `media`         | `MediaSession` observation and playback control.                      |
| `protocol`      | (De)serialisation between domain objects and the JSON wire protocol.   |

## Boundaries

* **Bluetooth code is isolated** in `bluetooth`. It knows about bytes and
  sockets, not about notifications or music.
* **Protocol code is isolated** in `protocol`. It is the only place that knows
  the JSON shapes defined in [`docs/protocol/PROTOCOL.md`](../docs/protocol/PROTOCOL.md).
* Feature modules (`notifications`, `media`, …) translate Android framework
  events into `core` domain objects and never touch the wire format directly.

## Build

A Gradle project will be added in **Milestone 2**, when the first real code (the
Bluetooth handshake server) lands. This milestone establishes structure and the
shared protocol only.
