package com.uit.finance.core.session

/**
 * Dữ liệu theo tài khoản nằm ngoài sổ (ví dụ cài đặt riêng của từng tài khoản). Module nào giữ dữ liệu kiểu này
 * đăng ký một cleaner vào Koin; [UserSession] gọi tất cả khi xoá sổ của tài khoản (đăng xuất, xoá tài khoản,
 * dọn tài khoản khác trên máy). Không gọi khi chỉ hết phiên (B7): đăng nhập lại đúng người thì giữ nguyên.
 */
fun interface UserDataCleaner {
    suspend fun clear(userId: String)
}
