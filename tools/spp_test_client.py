#!/usr/bin/env python3
"""
RelayME SPP test client.

Impersonates the Java ME client so the Android Companion can be tested from a
Linux machine (with a real Bluetooth adapter) instead of a feature phone.

It connects to the Android app's RFCOMM/SPP service, performs the Milestone 2
handshake (waits for the server's `hello`, replies with a `javame` hello), then
prints every line the phone sends and lets you type protocol lines back.

Requirements:
  - Linux with BlueZ and a working Bluetooth adapter (in a VM: pass through a
    USB Bluetooth dongle).
  - Python 3 built with Bluetooth socket support (standard on Linux CPython).
  - The phone already paired:  bluetoothctl -> power on / pair / trust <MAC>.

Find the RelayME RFCOMM channel:
  sdptool browse <PHONE_MAC>        # look for service name "RelayME"

Usage:
  python3 spp_test_client.py <PHONE_MAC> [channel]
  e.g.  python3 spp_test_client.py AA:BB:CC:DD:EE:FF 1
"""

import socket
import sys
import threading


def reader(sock):
    """Print newline-delimited lines from the phone; reply to the handshake."""
    buf = bytearray()
    while True:
        try:
            chunk = sock.recv(1024)
        except OSError:
            break
        if not chunk:
            print("\n[disconnected]")
            break
        buf.extend(chunk)
        while b"\n" in buf:
            line, _, rest = buf.partition(b"\n")
            buf = bytearray(rest)
            text = line.decode("utf-8", "replace").strip()
            if not text:
                continue
            print("\n<= " + text)
            if '"type":"hello"' in text and '"role":"android"' in text:
                hello = '{"type":"hello","role":"javame","v":1,"name":"Linux SPP tester"}'
                sock.sendall((hello + "\n").encode("utf-8"))
                print("=> " + hello + "   (handshake reply sent)")
            print("type a JSON line (or 'quit'): ", end="", flush=True)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    mac = sys.argv[1]
    channel = int(sys.argv[2]) if len(sys.argv) > 2 else 1

    sock = socket.socket(
        socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM
    )
    print("Connecting to %s channel %d ..." % (mac, channel))
    sock.connect((mac, channel))
    print("Connected. Waiting for the server's hello.\n")

    threading.Thread(target=reader, args=(sock,), daemon=True).start()

    # Let the tester send arbitrary protocol lines (e.g. {"cmd":"ping"}).
    try:
        for line in sys.stdin:
            line = line.strip()
            if line in ("quit", "exit"):
                break
            if line:
                sock.sendall((line + "\n").encode("utf-8"))
    except KeyboardInterrupt:
        pass
    finally:
        sock.close()


if __name__ == "__main__":
    main()
