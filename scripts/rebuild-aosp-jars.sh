#!/usr/bin/env bash
# Rebuild the three remaining Android 16 inputs from an existing AOSP checkout.
set -eo pipefail
if [[ $# != 2 ]]; then
  echo "Usage: bash scripts/rebuild-aosp-jars.sh AOSP_CHECKOUT OUTPUT_DIRECTORY" >&2
  exit 2
fi
aosp=$(realpath "$1")
mkdir -p "$2"
dest=$(realpath "$2")
cd "$aosp"
test -f build/envsetup.sh
# Require the documented AOSP tag for frameworks/base.
test "$(git -C frameworks/base rev-parse HEAD)" = "$(git -C frameworks/base rev-parse refs/tags/android-16.0.0_r3^{commit})"
repo manifest -r -o "$dest/aosp-manifest.xml"
source build/envsetup.sh
lunch aosp_cf_x86_64_only_phone-aosp_current-userdebug
m SystemUI-core SystemUI-statsd WindowManager-Shell
base="${OUT_DIR:-out}/soong/.intermediates/frameworks/base"
install -m 0644 "$base/packages/SystemUI/SystemUI-core/android_common/javac/SystemUI-core.jar" "$dest/SystemUI-core-16.jar"
install -m 0644 "$base/packages/SystemUI/shared/SystemUI-statsd/android_common/javac/SystemUI-statsd.jar" "$dest/SystemUI-statsd-16.jar"
install -m 0644 "$base/libs/WindowManager/Shell/WindowManager-Shell/android_common/javac/WindowManager-Shell.jar" "$dest/WindowManager-Shell-16.jar"
(cd "$dest"; sha256sum SystemUI-core-16.jar SystemUI-statsd-16.jar WindowManager-Shell-16.jar > SHA256SUMS)
