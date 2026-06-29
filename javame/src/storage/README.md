# javame / storage

Local persistence backed by RMS (Record Management System), the only persistent
store available on MIDP 2.0.

**Why it exists:** to isolate RMS record handling so the UI and protocol layers
work with simple objects, not record stores.

**Responsibilities**
- Persist received notifications so they survive app restarts (Milestone 4).
- Persist user settings (Milestone 8).

**Java ME limitation:** RMS stores opaque `byte[]` records keyed by integer id;
there is no query language. Records are serialised/deserialised by hand and kept
small to respect the device's limited storage quota.

**Does not contain** Bluetooth, JSON-wire, or UI code.
