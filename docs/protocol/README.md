# RelayME Protocol Documentation

This folder is the **shared contract** between the two RelayME applications. It
is deliberately kept separate from either codebase so that neither side "owns"
the protocol — both implement against the spec.

* **[`PROTOCOL.md`](./PROTOCOL.md)** — the complete wire protocol: framing,
  message classes, versioning, the full event/command catalogue, and a worked
  example session.

## Rules for changing the protocol

1. **Extend, never fork.** Add new message types or optional fields. Do not
   create a second, parallel format.
2. **Backward compatible by default.** New additions must not break an older
   peer (see the robustness rules in `PROTOCOL.md` §7).
3. **Bump the version only for breaking changes** and record it in the change
   log (`PROTOCOL.md` §11).
4. Update **both** the Android `protocol` module and the Java ME `protocol`
   package in the same change set as the spec.
