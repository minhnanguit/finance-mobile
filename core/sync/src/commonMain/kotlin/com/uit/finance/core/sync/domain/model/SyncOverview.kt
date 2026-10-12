package com.uit.finance.core.sync.domain.model

/**
 * Trạng thái đồng bộ cho UI (badge "chưa đồng bộ", kéo để làm mới).
 *
 * @param pending thay đổi trên máy chưa lên server
 * @param stuck trong số [pending], thay đổi server đã nhiều lần chưa nhận (vẫn đang thử lại)
 * @param rejected thay đổi server từ chối hẳn, đã khôi phục theo bản của server; user chưa xem
 * @param isSyncing một vòng sync đang chạy
 */
data class SyncOverview(
    val pending: Long = 0,
    val stuck: Long = 0,
    val rejected: Long = 0,
    val isSyncing: Boolean = false,
) {
    /** Có gì cần cho user biết. */
    val needsAttention: Boolean get() = pending > 0 || rejected > 0

    companion object {
        val IDLE = SyncOverview()
    }
}
