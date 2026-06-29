# android / app

Android application module — the only module with framework entry points.

**Why it exists:** to wire the independent feature modules together behind a
persistent foreground service and a thin UI, and to own the manifest,
permissions, and device-pairing flow.

**Responsibilities**
- Persistent background (foreground) service that hosts the Bluetooth server.
- Device pairing / connection UI.
- Permission requests (notification access, Bluetooth, etc.).
- Dependency wiring between `bluetooth`, `protocol`, and the feature modules.

**Does not contain** Bluetooth socket logic, protocol parsing, or feature
observation — those live in their dedicated modules.
