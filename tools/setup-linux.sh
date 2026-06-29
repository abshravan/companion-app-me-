#!/usr/bin/env bash
#
# RelayME development environment setup for Linux (Void and Debian/Ubuntu).
#
# Automates the reliable parts of the testing setup on a bare-metal box:
#   1. system packages (JDK 17, adb, BlueZ, Ant, Python, helpers)
#   2. the Bluetooth stack (dbus + bluetooth service)
#   3. the Android SDK (command-line tools + platform/build-tools)
#   4. android/local.properties pointing at the SDK
#   5. the protocol logic tests on both sides (no phones/Bluetooth needed)
#
# Distro support is auto-detected:
#   - Void Linux     : xbps-install + runit services
#   - Debian/Ubuntu  : apt-get + systemd services
#
# It is idempotent: re-running skips work that is already done. The Java ME
# device toolchain (Wireless Toolkit) cannot be auto-installed because it is not
# freely downloadable; the script prints exact manual steps for it instead.
#
# Usage:
#   tools/setup-linux.sh [options]
#
# Options:
#   --no-android     skip Android SDK installation
#   --no-bluetooth   skip BlueZ install + service enable
#   --no-tests       skip running the protocol logic tests
#   --sdk-dir DIR    Android SDK location (default: $HOME/Android/Sdk)
#   -h, --help       show this help
#
set -euo pipefail

# ---- configuration -----------------------------------------------------------

# Pinned command-line-tools build (update if Google rotates it; the file is the
# "latest" of this build series). See https://developer.android.com/studio
CMDLINE_TOOLS_BUILD="11076708"
ANDROID_PLATFORM="android-34"
ANDROID_BUILD_TOOLS="34.0.0"

SDK_DIR="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
DO_ANDROID=1
DO_BLUETOOTH=1
DO_TESTS=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

PKG_MANAGER=""   # xbps | apt
SVC_MANAGER=""   # runit | systemd

# ---- helpers -----------------------------------------------------------------

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
info() { printf '  • %s\n' "$*"; }
warn() { printf '\033[33m  ! %s\033[0m\n' "$*"; }
die()  { printf '\033[31mError: %s\033[0m\n' "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }

usage() {
    cat <<'EOF'
RelayME development environment setup for Linux (Void and Debian/Ubuntu).

Usage:
  tools/setup-linux.sh [options]

Options:
  --no-android     skip Android SDK installation
  --no-bluetooth   skip BlueZ install + service enable
  --no-tests       skip running the protocol logic tests
  --sdk-dir DIR    Android SDK location (default: $HOME/Android/Sdk)
  -h, --help       show this help
EOF
    exit 0
}

# Run a command with sudo, or directly if already root.
sudo_run() {
    if [ "$(id -u)" -eq 0 ]; then "$@"; else sudo "$@"; fi
}

# ---- argument parsing --------------------------------------------------------

while [ $# -gt 0 ]; do
    case "$1" in
        --no-android)   DO_ANDROID=0 ;;
        --no-bluetooth) DO_BLUETOOTH=0 ;;
        --no-tests)     DO_TESTS=0 ;;
        --sdk-dir)      shift; SDK_DIR="${1:-}"; [ -n "$SDK_DIR" ] || die "--sdk-dir needs a path" ;;
        -h|--help)      usage ;;
        *)              die "unknown option: $1 (try --help)" ;;
    esac
    shift
done

# ---- steps -------------------------------------------------------------------

detect_environment() {
    bold "Detecting environment"
    if have xbps-install; then
        PKG_MANAGER="xbps"
    elif have apt-get; then
        PKG_MANAGER="apt"
    else
        die "no supported package manager found (need xbps-install or apt-get)."
    fi

    # Prefer systemd when it is the active init; otherwise runit (Void).
    if have systemctl && [ -d /run/systemd/system ]; then
        SVC_MANAGER="systemd"
    elif [ -d /etc/sv ] || have sv; then
        SVC_MANAGER="runit"
    else
        SVC_MANAGER="none"
    fi

    info "Package manager: $PKG_MANAGER"
    info "Service manager: $SVC_MANAGER"
}

install_packages() {
    bold "Installing system packages"
    local pkgs
    case "$PKG_MANAGER" in
        xbps)
            # Void package names.
            pkgs="openjdk17 android-tools python3 unzip wget apache-ant"
            [ "$DO_BLUETOOTH" -eq 1 ] && pkgs="$pkgs bluez dbus"
            info "xbps-install: $pkgs"
            sudo_run xbps-install -Sy $pkgs
            ;;
        apt)
            # Debian/Ubuntu package names.
            pkgs="openjdk-17-jdk adb python3 unzip wget ant"
            [ "$DO_BLUETOOTH" -eq 1 ] && pkgs="$pkgs bluez dbus"
            info "apt-get update"
            sudo_run apt-get update
            info "apt-get install: $pkgs"
            DEBIAN_FRONTEND=noninteractive sudo_run apt-get install -y $pkgs
            ;;
    esac

    detect_java_home
}

# Derive JAVA_HOME from the installed javac, portably across distros.
detect_java_home() {
    local jc; jc="$(command -v javac || true)"
    if [ -n "$jc" ]; then
        JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$jc")")")"
        export JAVA_HOME
        info "JAVA_HOME=$JAVA_HOME"
        java -version 2>&1 | head -n1 | sed 's/^/    /'
    else
        warn "javac not found after install; ensure a JDK 17 is on PATH."
    fi
}

enable_bluetooth() {
    [ "$DO_BLUETOOTH" -eq 1 ] || { info "Skipping Bluetooth (--no-bluetooth)."; return; }
    bold "Enabling Bluetooth services"
    case "$SVC_MANAGER" in
        runit)
            for svc in dbus bluetoothd; do
                if [ -d "/etc/sv/$svc" ]; then
                    if [ -e "/var/service/$svc" ]; then
                        info "$svc already enabled."
                    else
                        sudo_run ln -s "/etc/sv/$svc" /var/service/
                        info "$svc enabled."
                    fi
                else
                    warn "service definition /etc/sv/$svc missing; is the package installed?"
                fi
            done
            ;;
        systemd)
            # dbus is always running under systemd; just the bluetooth daemon.
            if sudo_run systemctl enable --now bluetooth; then
                info "bluetooth.service enabled and started."
            else
                warn "could not enable bluetooth.service; check 'systemctl status bluetooth'."
            fi
            ;;
        *)
            warn "unknown service manager; start dbus + bluetoothd manually."
            ;;
    esac
    info "Power on the adapter later with:  bluetoothctl power on"
}

install_android_sdk() {
    [ "$DO_ANDROID" -eq 1 ] || { info "Skipping Android SDK (--no-android)."; return; }
    bold "Installing the Android SDK into $SDK_DIR"

    local sdkmanager="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
    if [ ! -x "$sdkmanager" ]; then
        local zip="commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip"
        local url="https://dl.google.com/android/repository/$zip"
        local tmp; tmp="$(mktemp -d)"
        info "Downloading $zip"
        wget -q --show-progress -O "$tmp/$zip" "$url" \
            || die "download failed; check CMDLINE_TOOLS_BUILD or your network."
        info "Unpacking command-line tools"
        unzip -q "$tmp/$zip" -d "$tmp"
        # Google ships them as cmdline-tools/*, but the SDK wants them under
        # cmdline-tools/latest/.
        mkdir -p "$SDK_DIR/cmdline-tools/latest"
        cp -r "$tmp/cmdline-tools/." "$SDK_DIR/cmdline-tools/latest/"
        rm -rf "$tmp"
    else
        info "command-line tools already present."
    fi

    info "Accepting licenses and installing components"
    yes | "$sdkmanager" --sdk_root="$SDK_DIR" --licenses >/dev/null
    "$sdkmanager" --sdk_root="$SDK_DIR" \
        "platform-tools" \
        "platforms;$ANDROID_PLATFORM" \
        "build-tools;$ANDROID_BUILD_TOOLS"
    info "SDK ready: $ANDROID_PLATFORM, build-tools $ANDROID_BUILD_TOOLS."
}

configure_local_properties() {
    [ "$DO_ANDROID" -eq 1 ] || return
    bold "Configuring android/local.properties"
    local props="$REPO_ROOT/android/local.properties"
    printf 'sdk.dir=%s\n' "$SDK_DIR" > "$props"
    info "Wrote $props"
}

run_logic_tests() {
    [ "$DO_TESTS" -eq 1 ] || { info "Skipping tests (--no-tests)."; return; }
    bold "Running protocol logic tests (no phones/Bluetooth required)"

    info "Android codec (Gradle):"
    ( cd "$REPO_ROOT/android" && ./gradlew --console=plain :protocol:test ) \
        || warn "Android codec tests failed — check the output above."

    info "Java ME codec (javac/java):"
    local out; out="$(mktemp -d)"
    if javac -d "$out" "$REPO_ROOT"/javame/src/protocol/*.java \
                       "$REPO_ROOT"/javame/test/protocol/*.java 2>/dev/null; then
        java -cp "$out" protocol.ProtocolTest || warn "Java ME tests failed."
    else
        warn "Java ME compile failed — check your JDK."
    fi
    rm -rf "$out"
}

print_summary() {
    bold "Done. Next steps"
    cat <<EOF

  Android app (build + install on your phone over USB, debugging enabled):
    cd $REPO_ROOT/android
    ./gradlew :app:assembleDebug
    adb install app/build/outputs/apk/debug/app-debug.apk

  Test the link from this PC (Layer 2 — acts as the Java ME client):
    bluetoothctl                 # power on / scan on / pair <MAC> / trust <MAC>
    sdptool browse <PHONE_MAC>   # find the "RelayME" channel
    python3 $REPO_ROOT/tools/spp_test_client.py <PHONE_MAC> <channel>

  Java ME build (no Wireless Toolkit needed — all deps from Maven Central):
    cd $REPO_ROOT/javame
    ./gradlew assemble            # -> build/dist/RelayME.jar + RelayME.jad
    Copy build/dist/RelayME.jad + RelayME.jar to the phone, install, and
    launch; the Home screen reports whether the device exposes JSR-82.

EOF
}

# ---- main --------------------------------------------------------------------

bold "RelayME — Linux setup"
detect_environment
install_packages
enable_bluetooth
install_android_sdk
configure_local_properties
run_logic_tests
print_summary
