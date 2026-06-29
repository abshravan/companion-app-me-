# android / media

Observes and controls media playback via the Android `MediaSession` framework.

**Why it exists:** to isolate now-playing observation and transport controls so
the rest of the app deals only with a clean media state model and intent.

**Responsibilities (Milestone 6)**
- Use `MediaSessionManager` / `MediaController` to read the active session's
  metadata (title, artist) and playback state.
- Translate `music_*` commands into `MediaController.TransportControls` calls
  (play, pause, skip next/previous).

**Android limitation:** reading active sessions requires the
`NotificationListenerService` permission to be granted (the same access used by
the `notifications` module).

**Does not contain** Bluetooth or JSON code.
