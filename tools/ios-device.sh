#!/usr/bin/env bash
# Build bản Release và cài lên iPhone thật (cáp hoặc cùng Wi-Fi), rồi mở app. Gọi qua `make ios-device`.
# Apple ID free: app hết hạn sau 7 ngày — chạy lại lệnh này để gia hạn; cài đè nên dữ liệu trong app giữ nguyên.
set -euo pipefail
cd "$(dirname "$0")/.."

ROOT="$PWD"
DERIVED="$ROOT/build/ios-device"
XCCONFIG_LOCAL="iosApp/Configuration/Config.local.xcconfig"

fail() { echo "❌ $*" >&2; exit 1; }

# 1. Điều kiện trước khi build (báo lỗi rõ thay vì để xcodebuild in 200 dòng).
command -v xcodegen >/dev/null || fail "Thiếu xcodegen: brew install xcodegen"
grep -qs '^finance.releaseBaseUrl=https://' local.properties ||
  fail "Chưa có URL server. Chạy: make prod-url URL=https://xxx.duckdns.org"
grep -qsE '^TEAM_ID *= *[A-Z0-9]{10}' "$XCCONFIG_LOCAL" ||
  fail "Chưa có TEAM_ID. Thêm Apple ID vào Xcode (Settings → Accounts) rồi chạy: make ios-team"

# 2. Chọn iPhone: DEVICE=<identifier> hoặc máy thật đầu tiên đã pair.
DEVICE="${DEVICE:-}"
if [[ -z "$DEVICE" ]]; then
  JSON="$(mktemp)"
  xcrun devicectl list devices --json-output "$JSON" >/dev/null
  DEVICE="$(python3 - "$JSON" <<'PY'
import json, sys
devices = json.load(open(sys.argv[1]))["result"]["devices"]
for d in devices:
    hw = d.get("hardwareProperties", {})
    if hw.get("reality") == "physical" and hw.get("platform") == "iOS" \
            and d.get("connectionProperties", {}).get("pairingState") == "paired":
        print(d["identifier"]); break
PY
)"
  rm -f "$JSON"
  [[ -n "$DEVICE" ]] || fail "Không thấy iPhone nào đã pair. Cắm cáp, mở khoá máy, bấm Tin cậy; bật Developer Mode (Cài đặt → Quyền riêng tư & Bảo mật)."
fi
echo "📱 Thiết bị: $DEVICE"

# 3. Build Release (Gradle tự build framework Kotlin release qua build phase của Xcode).
(cd iosApp && xcodegen generate --quiet)
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -derivedDataPath "$DERIVED" \
  -allowProvisioningUpdates \
  -quiet \
  build

APP="$(find "$DERIVED/Build/Products/Release-iphoneos" -maxdepth 1 -name '*.app' | head -1)"
[[ -n "$APP" ]] || fail "Build xong nhưng không thấy file .app"
BUNDLE_ID="$(/usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' "$APP/Info.plist")"

# 4. Cài + mở.
xcrun devicectl device install app --device "$DEVICE" "$APP"
xcrun devicectl device process launch --device "$DEVICE" "$BUNDLE_ID" ||
  echo "⚠️  Cài xong nhưng chưa mở được. Lần đầu: Cài đặt → Cài đặt chung → VPN & Quản lý thiết bị → tin cậy Apple ID của bạn, rồi mở app."
echo "✅ Đã cài $BUNDLE_ID. Hết hạn sau 7 ngày — chạy lại 'make ios-device' trước ngày $(date -v+6d +%d/%m)."
