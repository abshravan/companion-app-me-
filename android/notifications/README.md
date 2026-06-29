# android / notifications

Bridges Android system notifications into RelayME domain events.

**Why it exists:** to encapsulate the `NotificationListenerService` integration
so notification capture, filtering, and dismissal live behind a single port.

**Responsibilities (Milestone 4)**
- Implement `NotificationListenerService` to receive posted/removed
  notifications.
- Map `StatusBarNotification` → `core` notification model.
- Honour `notification_dismiss` commands by cancelling the matching
  notification.

**Android limitation:** notification access must be granted manually by the user
in *Settings → Notification access*; it cannot be requested with a runtime
permission dialog.

**Does not contain** Bluetooth or JSON code — it produces/consumes `core`
models only.
