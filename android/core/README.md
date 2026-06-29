# android / core

Framework-independent domain layer.

**Why it exists:** to hold the business rules and data models (notifications,
calls, media state, battery, commands) in pure Kotlin, with **no Android and no
Bluetooth dependencies**. This keeps the rules testable and keeps every other
module pointing inward toward a stable centre (Clean Architecture).

**Responsibilities**
- Domain models shared across modules.
- Use-cases / interfaces that outer modules implement (e.g. a transport port,
  a notification source port).

**Does not contain** any `import android.*` or `import javax.bluetooth.*`.
