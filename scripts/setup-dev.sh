#!/usr/bin/env bash
# One-time dev setup: JDK + Gradle (via mise), Android SDK, emulator image, AVD.
# Re-runnable; installs under $HOME only.
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
API="${CONDUIT_TEST_API:-37.0}"     # emulator/platform version
BUILD_TOOLS="${CONDUIT_BUILD_TOOLS:-37.0.0}"
CMDTOOLS_ZIP="commandlinetools-linux-16111833_latest.zip"

mise trust -q mise.toml >/dev/null 2>&1 || true
mise install
JAVA_HOME="$(mise where java)"; export JAVA_HOME

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp="$(mktemp -d)"
  curl -sSLo "$tmp/c.zip" "https://dl.google.com/android/repository/$CMDTOOLS_ZIP"
  unzip -q "$tmp/c.zip" -d "$tmp"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi
SM="$SDK/cmdline-tools/latest/bin/sdkmanager"
"$SM" --sdk_root="$SDK" --install \
  "platform-tools" "emulator" "build-tools;$BUILD_TOOLS" \
  "platforms;android-$API" "system-images;android-$API;google_apis;x86_64"

if ! "$SDK/cmdline-tools/latest/bin/avdmanager" list avd -c | grep -qx conduit; then
  echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd -n conduit \
    -k "system-images;android-$API;google_apis;x86_64" -d pixel_8
fi
echo "sdk.dir=$SDK" > local.properties
echo "Done. SDK: $SDK  JAVA_HOME: $JAVA_HOME"
