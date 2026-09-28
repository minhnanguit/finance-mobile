# Mỗi target làm đúng MỘT việc. Muốn chạy nối tiếp thì liệt kê nhiều target:
#   make emulator wait install open     (mở máy ảo -> chờ boot -> cài -> mở app)
#   make install open                   (vòng lặp thường ngày sau khi sửa code)
# Gõ `make` hoặc `make help` để xem danh sách.

GRADLE      := ./gradlew
# Đường dẫn Android SDK: ưu tiên local.properties, rồi ANDROID_HOME, cuối cùng là vị trí mặc định.
ANDROID_SDK := $(firstword $(shell sed -n 's|^sdk.dir=||p' local.properties 2>/dev/null) $(ANDROID_HOME) $(HOME)/Library/Android/sdk)
ADB         := $(ANDROID_SDK)/platform-tools/adb
EMULATOR    := $(ANDROID_SDK)/emulator/emulator
AVD         ?= Pixel_8
# Tự chọn máy ảo đang chạy; nếu không thấy thì dùng cờ -e (máy ảo duy nhất).
# Muốn chỉ định tay: make log DEVICE=emulator-5556
DEVICE      ?= $(shell $(ANDROID_SDK)/platform-tools/adb devices 2>/dev/null | awk '/^emulator-[0-9]+[ \t]+device/{print $$1; exit}')
ADBD         = $(ADB) $(if $(DEVICE),-s $(DEVICE),-e)
PKG         := com.uit.finance
ACTIVITY    := $(PKG)/.app.MainActivity

.DEFAULT_GOAL := help
.PHONY: help emulator wait devices install open stop uninstall log crash api ios apk test arch lint tunnel tunnel-off clean

help: ## Liệt kê mọi lệnh make của repo này
	@echo "finance-mobile — các lệnh có sẵn:"
	@grep -E '^[a-z][a-zA-Z_-]*:.*## ' $(MAKEFILE_LIST) \
	  | awk 'BEGIN{FS=":.*## "}{printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2}'

# ---------- Máy ảo ----------

emulator: ## Mở máy ảo Android (mặc định Pixel_8, đổi bằng AVD=ten_khac) chạy nền, không chiếm terminal
	@nohup $(EMULATOR) -avd $(AVD) >/dev/null 2>&1 &
	@echo "Đang mở $(AVD)... chạy 'make wait' nếu muốn chờ boot xong"

wait: ## Chờ tới khi máy ảo boot xong và sẵn sàng nhận lệnh
	@$(ADB) -e wait-for-device
	@while [ "$$($(ADB) -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do sleep 2; done
	@echo "Máy ảo đã sẵn sàng"

devices: ## Liệt kê mọi máy ảo/máy thật mà adb đang thấy
	@$(ADB) devices -l

# ---------- Backend public qua Cloudflare Tunnel ----------
# URL lấy từ `make tunnel` bên finance-backend. Ghi vào local.properties (git-ignored), rồi `make install`.

tunnel: ## Trỏ app debug tới URL HTTPS của tunnel: make tunnel URL=https://xxx.trycloudflare.com
	@test -n "$(URL)" || { echo "Thiếu URL. Dùng: make tunnel URL=https://xxx.trycloudflare.com"; exit 1; }
	@case "$(URL)" in https://*) ;; *) echo "URL phải là https://"; exit 1;; esac
	@touch local.properties; grep -v '^finance.publicBaseUrl=' local.properties > local.properties.tmp || true
	@echo "finance.publicBaseUrl=$(URL)" >> local.properties.tmp && mv local.properties.tmp local.properties
	@echo "✅ Debug build sẽ gọi $(URL). Chạy 'make install' để build lại."

tunnel-off: ## Quay về loopback của emulator (http://10.0.2.2)
	@touch local.properties; grep -v '^finance.publicBaseUrl=' local.properties > local.properties.tmp || true
	@mv local.properties.tmp local.properties
	@echo "✅ Đã bỏ URL tunnel. Chạy 'make install' để build lại."

# ---------- Vòng lặp sửa code ----------

install: ## Build code mới rồi cài đè APK debug lên máy ảo
	$(GRADLE) :composeApp:installDebug

open: ## Mở app trên máy ảo (không build lại gì cả)
	@$(ADBD) shell am start -n $(ACTIVITY)

stop: ## Tắt hẳn app đang chạy trên máy ảo
	@$(ADBD) shell am force-stop $(PKG)

uninstall: ## Gỡ app khỏi máy ảo
	@$(ADBD) uninstall $(PKG)

# ---------- Log ----------

log: ## Xem log realtime của riêng app (Ctrl+C để thoát)
	@pid=$$($(ADBD) shell pidof $(PKG) 2>/dev/null | tr -d '\r'); \
	if [ -z "$$pid" ]; then echo "App chưa chạy — gõ 'make open' trước đã"; exit 1; fi; \
	$(ADBD) logcat --pid=$$pid

crash: ## Chỉ theo dõi cảnh báo và crash, bỏ qua log rác của hệ thống
	@$(ADBD) logcat '*:W' | grep -i --line-buffered 'finance\|AndroidRuntime'

# ---------- Build & kiểm thử ----------

api: ## Sinh lại Kotlin client từ api/openapi.yaml rồi biên dịch core/network
	$(GRADLE) :core:network:openApiGenerate :core:network:assemble

ios: ## Biên dịch klib iOS để kiểm tra code iosMain (KHÔNG cần Xcode)
	$(GRADLE) compileKotlinIosSimulatorArm64

apk: ## Chỉ build file APK debug, KHÔNG cài vào máy ảo
	$(GRADLE) :composeApp:assembleDebug

test: ## Chạy unit test của mọi module trên máy JVM
	$(GRADLE) testDebugUnitTest

arch: ## Kiểm tra luật kiến trúc Konsist (presentation !-> data, domain thuần...)
	$(GRADLE) :architecture-test:test

lint: ## Chạy Android Lint cho module app
	$(GRADLE) :composeApp:lintDebug

clean: ## Xoá thư mục build/ của mọi module
	$(GRADLE) clean
