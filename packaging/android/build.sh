#!/usr/bin/env bash
set -euo pipefail
GORO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$GORO_ROOT"
BUILD_TYPE=${ANDROID_BUILD_TYPE:-debug}
case "$BUILD_TYPE" in
    debug) ;;
    release)
        : "${ANDROID_KEYSTORE:?Set ANDROID_KEYSTORE to the release keystore path}"
        : "${ANDROID_KEYSTORE_PASSWORD:?Set ANDROID_KEYSTORE_PASSWORD}"
        : "${ANDROID_VERSION_NAME:?Set ANDROID_VERSION_NAME}"
        : "${ANDROID_VERSION_CODE:?Set ANDROID_VERSION_CODE to a positive integer}"
        if [[ ! -f "$ANDROID_KEYSTORE" ]]; then echo "Release keystore not found: $ANDROID_KEYSTORE" >&2; exit 1; fi
        ;;
    *) echo "ANDROID_BUILD_TYPE must be debug or release" >&2; exit 1 ;;
esac
VERSION_CODE=${ANDROID_VERSION_CODE:-1}
if [[ ! "$VERSION_CODE" =~ ^[1-9][0-9]{0,9}$ ]] || ((VERSION_CODE > 2100000000)); then
    echo "ANDROID_VERSION_CODE must be an integer from 1 to 2100000000" >&2; exit 1
fi
SDK=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}
NDK=${ANDROID_NDK_HOME:-$(find "$SDK/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)}
ANDROID_JAR="$SDK/platforms/android-35/android.jar"
OUT="$GORO_ROOT/dist/android"
for tool in "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang" "$ANDROID_JAR"; do
    if [[ ! -f "$tool" ]]; then echo "Missing Android build dependency: $tool" >&2; exit 1; fi
done
command -v gradle >/dev/null || { echo "Gradle 8.9+ is required (the APK is now assembled by Gradle)" >&2; exit 1; }
mkdir -p "$OUT"/{lib/arm64-v8a,overlay}
export GOCACHE=${GOCACHE:-/tmp/goro-go-cache}
# The Android patches need dependency sources before go build can download them.
go mod download github.com/gogpu/gogpu github.com/ebitengine/oto/v3
GOGPU_DIR=$(go list -m -f '{{.Dir}}' github.com/gogpu/gogpu)
GOGPU_VERSION=$(go list -m -f '{{.Version}}' github.com/gogpu/gogpu)
if [[ "$GOGPU_VERSION" != v0.54.0 ]]; then
    echo "The Android platform overlay needs review for GoGPU $GOGPU_VERSION (expected v0.54.0)." >&2; exit 1
fi
# Go forbids overlays inside its immutable module cache. Work from a private
# copy and a temporary modfile; the project's desktop module graph is untouched.
if [[ ! -d "$OUT/gogpu-$GOGPU_VERSION" ]]; then
    cp -a "$GOGPU_DIR" "$OUT/gogpu-$GOGPU_VERSION"
fi
cp go.mod "$OUT/android.mod"
cp go.sum "$OUT/android.sum"
go mod edit -modfile "$OUT/android.mod" -replace "github.com/gogpu/gogpu=$OUT/gogpu-$GOGPU_VERSION"
python3 packaging/android/overlay.py "$OUT/gogpu-$GOGPU_VERSION" "$OUT/overlay"
# Oto's bundled Oboe predates the final NDK 30 AAudio declarations. Patch
# only a private copy when those declarations are present in the selected NDK.
AAUDIO_HEADER="$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/aaudio/AAudio.h"
if rg -q 'typedef enum AAudio_FallbackMode' "$AAUDIO_HEADER"; then
    OTO_DIR=$(go list -m -f '{{.Dir}}' github.com/ebitengine/oto/v3)
    OTO_VERSION=$(go list -m -f '{{.Version}}' github.com/ebitengine/oto/v3)
    if [[ "$OTO_VERSION" != v3.5.0-alpha.8 ]]; then
        echo "Review the NDK 30 audio compatibility patch for Oto $OTO_VERSION." >&2; exit 1
    fi
    if [[ ! -d "$OUT/oto-$OTO_VERSION" ]]; then
        cp -a "$OTO_DIR" "$OUT/oto-$OTO_VERSION"
        chmod -R u+w "$OUT/oto-$OTO_VERSION"
    fi
    python3 packaging/android/patch-oboe.py "$OTO_DIR" "$OUT/oto-$OTO_VERSION"
    go mod edit -modfile "$OUT/android.mod" -replace "github.com/ebitengine/oto/v3=$OUT/oto-$OTO_VERSION"
fi
export CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang"
export CXX="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang++"
GOOS=android GOARCH=arm64 CGO_ENABLED=1 go build \
    -modfile "$OUT/android.mod" -overlay "$OUT/overlay/overlay.json" -buildmode=c-shared \
    -ldflags='-s -w -extldflags=-Wl,-z,max-page-size=16384' \
    -o "$OUT/lib/arm64-v8a/libgoro.so" ./cmd/goro-android
export ANDROID_HOME="$SDK" ANDROID_BUILD_TYPE="$BUILD_TYPE" ANDROID_VERSION_CODE="$VERSION_CODE"
export ANDROID_VERSION_NAME="${ANDROID_VERSION_NAME:-0.1-dev}"
if [[ "$BUILD_TYPE" == debug ]]; then TASK=:app:assembleDebug; SRC=debug/app-debug.apk; APK="$OUT/goro-debug.apk"
else TASK=:app:assembleRelease; SRC=release/app-release.apk; APK="$OUT/goro-android-arm64.apk"; fi
gradle -p packaging/android --no-daemon --console=plain "$TASK"
cp "packaging/android/app/build/outputs/apk/$SRC" "$APK"
echo "Built $APK"
