# tools

Helper utilities for testing RelayME during development. Not part of either
shipped application.

## `spp_test_client.py`

A Linux stand-in for the Java ME client. It lets you test the **Android
Companion** over real Bluetooth from a PC (or a VM with a USB Bluetooth dongle
passed through) without needing a JSR-82 feature phone.

It connects to the Android app's SPP service, completes the Milestone 2
handshake, then prints incoming lines and forwards JSON lines you type.

### Prerequisites
- Linux with BlueZ; a working Bluetooth adapter.
- The Android phone paired and trusted:
  ```sh
  bluetoothctl
  # power on / agent on / scan on / pair <MAC> / trust <MAC>
  ```

### Run
```sh
# Start the Android app and tap "Start link" first, then:
sdptool browse <PHONE_MAC>                 # find the "RelayME" channel
python3 tools/spp_test_client.py <PHONE_MAC> <channel>
```

You should see the server's `hello` arrive, an automatic `javame` reply, and the
phone's screen change to "Hello Java …". Type lines like `{"cmd":"ping"}` to
poke later-milestone features as they land.
