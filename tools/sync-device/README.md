# sync-device — máy thứ hai giả lập

Đóng vai một máy khác cùng tài khoản để kiểm tra sync thật (LEDGER-PLAN Phase 6): ghi từ "máy B" rồi xem app
thấy gì, tạo xung đột có chủ đích, gửi lại `opId` cũ để kiểm `DUPLICATE`. Đăng nhập bằng Authorization Code +
PKCE y như app nên **không** cần bật direct grant trên Keycloak. Chỉ dùng Python stdlib.

## Chuẩn bị

1. Backend: `make up run` (+ `make tunnel` nếu app trỏ tunnel) bên `finance-backend`.
2. Tài khoản test đã verify email (tạo qua màn đăng ký + Mailpit, hoặc Keycloak Admin `make kc`).
3. `FINANCE_BASE` = URL Keycloak mà app đang dùng, vì token phải mang đúng issuer backend chờ. App trỏ tunnel →
   dùng URL tunnel. App chạy loopback (issuer `http://10.0.2.2:8081`) thì máy host không gọi được địa chỉ đó,
   còn `localhost:8081` cho ra issuer khác → bật `make tunnel`.

```bash
export FINANCE_BASE=https://xxx.trycloudflare.com
export FINANCE_API=http://localhost:8080      # tuỳ chọn: gọi API thẳng, nhanh hơn đi vòng tunnel
python3 -I sync_device.py login B user@example.test 'mật khẩu'
python3 -I sync_device.py pull B > changes.json
python3 -I sync_device.py push B < ops.json   # mảng SyncOperation theo api/openapi.yaml
```

Access token sống 5 phút; hết hạn thì `login` lại (giữ nguyên `deviceId` và cursor trong `state/`).

## Kịch bản Phase 6 (2026-10-12)

| Kịch bản | Cách làm | Thấy gì là đạt |
|---|---|---|
| Offline ghi 5 khoản rồi có mạng | `adb shell cmd connectivity airplane-mode enable/disable` | Server đủ 5 dòng, badge "chưa đồng bộ" về 0, **không chạm app** (WorkManager chạy lại khi có mạng) |
| Máy B ghi → máy A thấy | `push` ví + giao dịch, kéo để làm mới trên app | Số dư trên app khớp |
| 2 máy cùng sửa lúc offline | App sửa khi offline, B `push` bản khác trước, rồi app có mạng | Server, app, `pull` của B cùng một bản (bản tới sau) |
| Xoá thắng | B `DELETE` rồi app gửi bản sửa; và app xoá rồi B gửi bản sửa | `CONFLICT ledger.deleted`, bản ghi biến mất ở cả hai |
| Kill app giữa lúc gửi | Khoá `user_sync_state` của user trong psql (`SELECT … FOR UPDATE; SELECT pg_sleep(30)`), bật mạng, `am force-stop` khi thấy `REQUEST …/sync/push`, nhả khoá, mở lại app | Server đã ghi đúng 1 lần; app gửi lại cùng `opId`, hàng chờ về 0, không trùng dòng |
| B5 đổi người | Đăng xuất A (có khoản chờ → hiện cảnh báo), đăng nhập B | `run-as com.uit.finance ls databases` chỉ còn file của B; B không thấy gì của A |
| B6 | `adb exec-out run-as com.uit.finance cat databases/finance-<id>.db > x.db; sqlite3 x.db .tables` | `file is not a database` |
| B7 | Ghi offline, Keycloak Admin → user → Sessions → Sign out, đợi > 5 phút, bật mạng, đăng nhập lại | App về màn đăng nhập, file DB còn; đăng nhập lại thì khoản chờ được gửi |
| B8 | Gửi giá trị dễ grep (số tiền, ghi chú) rồi grep log backend + `adb logcat -d` | Không có dòng nào |
