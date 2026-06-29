# RelayME Wire Protocol

**Status:** Stable (v1)
**Transport:** Bluetooth RFCOMM / Serial Port Profile (SPP)
**Encoding:** UTF-8, line-delimited JSON (NDJSON)

This document is the single source of truth for communication between the
**Android Companion** and the **Java ME Client**. Both applications MUST
implement exactly what is described here. New features extend this document;
they never replace it. **Never invent a parallel protocol.**

---

## 1. Design goals

1. **Tiny on the Java ME side.** CLDC 1.1 / MIDP 2.0 has no JSON library, a
   small heap, and a slow CPU. The wire format must be parseable by a
   hand-written, allocation-light parser.
2. **Human-readable.** JSON makes the protocol easy to debug with a serial
   terminal and easy to extend.
3. **Self-delimiting.** A stream-oriented transport (RFCOMM) does not preserve
   message boundaries, so every message is terminated by a single newline.
4. **Forward-compatible.** Unknown message types and unknown fields are ignored,
   never fatal. This lets a newer Android app talk to an older Java ME client.

---

## 2. Framing

* Each message is a **single JSON object** on **one line**.
* Each message is terminated by exactly one `\n` (`0x0A`, LF).
* `\r` (`0x0D`) MUST NOT appear in the framing and SHOULD be tolerated/stripped
  by readers.
* A message MUST NOT contain a raw newline inside its payload. String values
  that need a newline use the JSON escape `\n`.
* Readers accumulate bytes until they see `\n`, then parse the buffered line.
* The maximum line length is **4096 bytes**. Senders MUST NOT exceed it;
  receivers MAY drop a line that exceeds it (see §7, error handling).

```
{"type":"hello","role":"android","v":1,"name":"Pixel 7"}\n
{"type":"notification","app":"WhatsApp","title":"John","text":"Meet me at 5"}\n
{"cmd":"music_next"}\n
```

---

## 3. Message classes

Every message is exactly one of two classes, distinguished by its key field:

| Class      | Key field | Direction              | Meaning                          |
|------------|-----------|------------------------|----------------------------------|
| **Event**  | `type`    | Android → Java ME      | Something happened on the phone. |
| **Command**| `cmd`     | Java ME → Android      | The watch asks the phone to act. |

A single object MUST NOT contain both `type` and `cmd`. The handshake
(`hello`) is the one message sent by **both** sides and uses `type`.

All field values are JSON strings, numbers, or booleans. Objects and arrays are
avoided wherever possible to keep the Java ME parser flat and cheap. Where a
nested structure is unavoidable it is called out explicitly.

---

## 4. Protocol versioning

* The current protocol version is **`1`**.
* The version is exchanged in the `hello` handshake via the `v` field.
* Minor, backward-compatible additions (new event/command names, new optional
  fields) do **not** bump the version.
* A breaking change (renamed field, changed semantics, changed framing) bumps
  `v`. Each side SHOULD refuse or downgrade gracefully if the peer reports a
  higher major version it does not understand.

---

## 5. Connection lifecycle

```
  Java ME (client)                         Android (server)
        |                                         |
        |  --- RFCOMM connect (SPP UUID) ------>  |   accept()
        |                                         |
        |  <----------- hello (android) --------  |   server greets first
        |  ------------ hello (javame) -------->  |
        |                                         |
        |  <=== events (notification, call …) === |
        |  === commands (music_next, find …) ==>  |
        |                                         |
        |  --- ping/pong keepalive (either) ----  |
        |                                         |
        |  (link drops) -> client auto-reconnect  |
```

* **Service discovery:** The Android app publishes an SPP service record. The
  Java ME client discovers it by UUID (see §6).
* **Greeting:** Immediately after the socket opens, the **Android side sends
  `hello` first**. The Java ME side replies with its own `hello`. Application
  events MUST NOT be sent before the local `hello` has been written.
* **Keepalive:** Either side MAY send `ping`; the peer replies `pong`. Absence
  of traffic for an implementation-defined window triggers reconnection on the
  Java ME side (see Milestone 3).

---

## 6. Identifiers

| Name                | Value                                    |
|---------------------|------------------------------------------|
| SPP Service UUID    | `1101` (well-known Serial Port Profile)  |
| Service record name | `RelayME`                                |

> **Java ME limitation:** JSR-82 (`javax.bluetooth`) is an **optional** package.
> A device must report `bluetooth.api.version` via `System.getProperty` for the
> client to function. The well-known 16-bit SPP UUID `0x1101` maximises
> compatibility with Series 40 Bluetooth stacks; a custom 128-bit UUID is
> avoided because some older stacks discover only short UUIDs reliably.

---

## 7. Robustness rules

Receivers MUST follow these rules so a malformed or unknown message can never
crash the peer:

1. **Unknown `type` / `cmd`** → ignore the message, keep the connection.
2. **Unknown extra fields** → ignore the field, process the rest.
3. **Missing optional field** → use the documented default.
4. **Missing required field** → ignore the whole message.
5. **Unparseable line / over-length line** → discard up to and including the
   next `\n`, then resume.

These rules are what make §4 versioning safe.

---

## 8. Event catalogue (Android → Java ME)

### 8.1 `hello`
Handshake. Sent first by Android, echoed by Java ME (see §3, §5).

| Field  | Type   | Req | Default | Notes                                   |
|--------|--------|-----|---------|-----------------------------------------|
| `type` | string | yes | —       | `"hello"`                               |
| `role` | string | yes | —       | `"android"` or `"javame"`               |
| `v`    | number | yes | —       | Protocol version, currently `1`         |
| `name` | string | no  | `""`    | Human-readable device name              |

```json
{"type":"hello","role":"android","v":1,"name":"Pixel 7"}
```

### 8.2 `notification` *(Milestone 4)*
A posted Android notification.

| Field   | Type   | Req | Default | Notes                                     |
|---------|--------|-----|---------|-------------------------------------------|
| `type`  | string | yes | —       | `"notification"`                          |
| `id`    | string | no  | `""`    | Stable key for dismissal/dedup            |
| `app`   | string | yes | —       | Source app label, e.g. `"WhatsApp"`       |
| `title` | string | no  | `""`    | Notification title                        |
| `text`  | string | no  | `""`    | Notification body (single line, `\n` esc) |
| `ts`    | number | no  | `0`     | Epoch millis when posted                  |

```json
{"type":"notification","id":"a1","app":"WhatsApp","title":"John","text":"Meet me at 5","ts":1719662400000}
```

### 8.3 `notification_removed` *(Milestone 4)*
Tells the watch a previously sent notification is gone.

| Field  | Type   | Req | Notes                |
|--------|--------|-----|----------------------|
| `type` | string | yes | `"notification_removed"` |
| `id`   | string | yes | Matches a prior `notification.id` |

### 8.4 `call` *(Milestone 5)*
Telephony state change.

| Field    | Type   | Req | Default | Notes                                   |
|----------|--------|-----|---------|-----------------------------------------|
| `type`   | string | yes | —       | `"call"`                                |
| `state`  | string | yes | —       | `"ringing"` \| `"active"` \| `"ended"`  |
| `number` | string | no  | `""`    | Caller number                           |
| `name`   | string | no  | `""`    | Resolved contact name                   |

```json
{"type":"call","state":"ringing","number":"+15551234567","name":"Mom"}
```

### 8.5 `music` *(Milestone 6)*
Now-playing / playback state from the active `MediaSession`.

| Field      | Type    | Req | Default | Notes                              |
|------------|---------|-----|---------|------------------------------------|
| `type`     | string  | yes | —       | `"music"`                          |
| `state`    | string  | yes | —       | `"playing"` \| `"paused"` \| `"stopped"` |
| `title`    | string  | no  | `""`    | Track title                        |
| `artist`   | string  | no  | `""`    | Track artist                       |

```json
{"type":"music","state":"playing","title":"Sicko Mode","artist":"Travis Scott"}
```

### 8.6 `battery` *(Milestone 7)*
Android battery status.

| Field       | Type    | Req | Default | Notes                          |
|-------------|---------|-----|---------|--------------------------------|
| `type`      | string  | yes | —       | `"battery"`                    |
| `level`     | number  | yes | —       | Percentage `0`–`100`           |
| `charging`  | boolean | no  | `false` | `true` while charging          |

```json
{"type":"battery","level":82,"charging":true}
```

### 8.7 `calendar` *(future)*
Upcoming calendar reminder.

| Field   | Type   | Req | Default | Notes                          |
|---------|--------|-----|---------|--------------------------------|
| `type`  | string | yes | —       | `"calendar"`                   |
| `title` | string | yes | —       | Event title                    |
| `start` | number | no  | `0`     | Epoch millis of event start    |
| `where` | string | no  | `""`    | Location                       |

### 8.8 `pong`
Keepalive reply (see §5, §9.5).

| Field  | Type   | Req | Notes      |
|--------|--------|-----|------------|
| `type` | string | yes | `"pong"`   |

---

## 9. Command catalogue (Java ME → Android)

### 9.1 `hello`
The Java ME handshake reply uses the **event** form with `role:"javame"`
(see §8.1). It is listed here only for cross-reference; it is the single
message both sides send.

### 9.2 Music control *(Milestone 6)*

| `cmd`          | Effect                                  |
|----------------|-----------------------------------------|
| `music_play`   | Resume playback                         |
| `music_pause`  | Pause playback                          |
| `music_toggle` | Toggle play/pause                       |
| `music_next`   | Skip to next track                      |
| `music_prev`   | Skip to previous track                  |

```json
{"cmd":"music_next"}
```

### 9.3 Call control *(Milestone 5)*

| `cmd`          | Effect                                  |
|----------------|-----------------------------------------|
| `call_reject`  | Reject / end the current call           |
| `call_silence` | Silence the ringer without rejecting    |

> **Android limitation:** Programmatically *answering* a call requires
> `ANSWER_PHONE_CALLS` (API 26+) and is intentionally out of scope for v1.
> Only reject and silence are specified.

### 9.4 `find_phone` *(future)*
Asks the phone to ring loudly so the user can locate it.

| Field  | Type   | Req | Notes                                   |
|--------|--------|-----|-----------------------------------------|
| `cmd`  | string | yes | `"find_phone"`                          |
| `on`   | boolean| no  | `true` = start ringing, `false` = stop. Default `true`. |

```json
{"cmd":"find_phone","on":true}
```

### 9.5 `notification_dismiss` *(Milestone 4)*
Dismiss the matching Android notification.

| Field  | Type   | Req | Notes                              |
|--------|--------|-----|------------------------------------|
| `cmd`  | string | yes | `"notification_dismiss"`           |
| `id`   | string | yes | Matches a prior `notification.id`  |

### 9.6 `ping`
Keepalive. Peer replies with `pong` (see §8.8).

| Field  | Type   | Req | Notes      |
|--------|--------|-----|------------|
| `cmd`  | string | yes | `"ping"`   |

---

## 10. Worked example session

```
A→J  {"type":"hello","role":"android","v":1,"name":"Pixel 7"}
J→A  {"type":"hello","role":"javame","v":1,"name":"Nokia 6300"}
A→J  {"type":"battery","level":82,"charging":true}
A→J  {"type":"notification","id":"a1","app":"WhatsApp","title":"John","text":"Meet me at 5"}
J→A  {"cmd":"notification_dismiss","id":"a1"}
A→J  {"type":"notification_removed","id":"a1"}
A→J  {"type":"music","state":"playing","title":"Sicko Mode","artist":"Travis Scott"}
J→A  {"cmd":"music_pause"}
A→J  {"type":"music","state":"paused","title":"Sicko Mode","artist":"Travis Scott"}
J→A  {"cmd":"ping"}
A→J  {"type":"pong"}
```

---

## 11. Change log

| Version | Date       | Change                          |
|---------|------------|---------------------------------|
| 1       | 2026-06-29 | Initial protocol specification. |
