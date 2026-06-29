# javame / ui

LCDUI screens for the client. Built to feel native to Nokia Series 40: simple,
fast, minimal memory.

**Why it exists:** to keep all presentation (LCDUI `Displayable`s, commands,
navigation) separate from transport and storage.

**Planned screens** (per the navigation model: Home, Notifications, Calls,
Music, Battery, Settings).

**Rules**
- Use `javax.microedition.lcdui` components unless a Java ME-compatible
  alternative is clearly justified.
- Keep screens lightweight; reuse `Displayable` instances where practical.
- The UI never opens sockets or touches RMS directly — it talks to the
  `bluetooth` and `storage` packages.
