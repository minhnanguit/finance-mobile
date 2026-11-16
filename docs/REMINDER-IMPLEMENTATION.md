# Nhắc ghi chép hằng ngày — kết quả triển khai

Trạng thái: **Hoàn thành và đã kiểm tra trên Android emulator ngày 2026-11-16.**

## Phạm vi

- Thêm cài đặt nhắc theo từng tài khoản tại `Cá nhân > Nhắc nhở`.
- Cho phép bật/tắt và chọn giờ nhắc.
- Yêu cầu quyền thông báo đúng luồng Android 13+; khi bị chặn có thể mở cài đặt hệ thống.
- Lập lịch trước tối đa 14 ngày và bỏ qua ngày đã có giao dịch.
- Tự lập lịch lại khi đăng nhập, đổi cài đặt, dữ liệu hôm nay thay đổi hoặc ứng dụng trở lại foreground.
- Android dùng WorkManager; iOS dùng UserNotifications.
- Bấm thông báo mở thẳng form `Thêm giao dịch`.
- Có nút debug `Thông báo thử sau 10 giây` để kiểm tra nhanh.

## Kiểm tra tự động

Đã chạy thành công:

```text
testDebugUnitTest
:architecture-test:test
assembleDebug
compileKotlinIosSimulatorArm64
```

Planner có kiểm tra: tắt tính năng, đủ 14 ngày, giờ hôm nay đã qua, hôm nay đã ghi giao dịch và chuyển múi giờ/DST.

## Kiểm tra thực tế Android

- Cài APK debug thành công trên Pixel 7 emulator.
- Mục nhắc nhở hiển thị đúng ở trang Cá nhân.
- Hộp thoại quyền thông báo xuất hiện và bật thành công.
- Sau khi bật, giao diện hiển thị `Giờ nhắc — 21:00`.
- Thông báo thử xuất hiện với channel `Nhắc nhở`.
- Bấm thông báo mở đúng form `Thêm giao dịch`.

## Ghi chú

Tính năng này hoàn toàn cục bộ, không thay đổi API hoặc mã nguồn backend.
