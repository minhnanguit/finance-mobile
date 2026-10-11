package com.uit.finance.core.database

/**
 * Nơi cất khoá của từng file DB (ADR-006 B6). `core/datastore` implement bằng `SecureStorage`
 * (Android Keystore / iOS Keychain), nên khoá không bao giờ nằm cạnh file DB ở dạng rõ.
 */
interface DatabaseKeyStore {
    /**
     * Khoá đang có của [userId]; `null` **chỉ khi** chắc chắn không còn: chưa từng tạo, hoặc đã mất hẳn
     * (Keystore bị reset). Tạm thời không đọc được (Keystore bận, máy chưa mở khoá lần nào) thì **ném lỗi**:
     * trả `null` lúc đó sẽ làm DB bị xoá oan, mất các thay đổi chưa gửi.
     */
    suspend fun find(userId: String): String?

    /** Tạo khoá ngẫu nhiên 256-bit mới cho [userId], ghi đè khoá cũ nếu có. */
    suspend fun create(userId: String): String

    suspend fun delete(userId: String)

    /** Mọi user đang có khoá trên máy này, tức là có DB đọc được. */
    suspend fun userIds(): Set<String>
}
