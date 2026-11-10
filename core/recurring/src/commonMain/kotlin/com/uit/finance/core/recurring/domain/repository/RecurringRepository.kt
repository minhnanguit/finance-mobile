package com.uit.finance.core.recurring.domain.repository

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.recurring.domain.model.LocalOccurrence
import com.uit.finance.core.recurring.domain.model.OccurrenceResult
import com.uit.finance.core.recurring.domain.model.OccurrenceStatus
import com.uit.finance.core.recurring.domain.model.Recurring
import com.uit.finance.core.recurring.domain.model.RecurringDraft
import com.uit.finance.core.recurring.domain.model.RevisionChange
import com.uit.finance.core.recurring.domain.model.ScheduledOccurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/*
 * Mọi lần ghi lưu vào DB của user trên máy rồi mới gửi lên server (offline-first). Vi phạm luật trả
 * `AppError.Validation` với mã trong `RecurringErrorCode`. Mỗi lần ghi khoản gửi kèm revision local làm điều kiện
 * (ADR-008 RS8, RS9); op của khoản định kỳ không bao giờ bị gộp.
 */
interface RecurringRepository {
    /** Khoản chưa xoá, kèm timeline và cờ còn op chờ gửi; tự đổi theo user đang đăng nhập. */
    fun observeRecurrings(): Flow<List<Recurring>>

    /** Kỳ do chính máy này sinh (R16). */
    fun observeLocalOccurrences(): Flow<List<LocalOccurrence>>

    /** Ảnh chụp hiện tại cho generator; chưa có user thì rỗng. */
    suspend fun snapshot(): List<Recurring>

    suspend fun create(draft: RecurringDraft): AppResult<Recurring>

    /** Đổi tên, số ngày nhắc trước: áp ngay, không tạo phiên bản. */
    suspend fun updateDetails(recurringId: String, name: String, remindDaysBefore: Int?): AppResult<Recurring>

    /** Phiên bản mới từ `change.effectiveFrom` (≥ hôm nay); phiên bản tương lai từ ngày đó trở đi bị thay (R10). */
    suspend fun revise(recurringId: String, change: RevisionChange): AppResult<Recurring>

    /** Kết thúc: [lastDate] là ngày cuối còn sinh kỳ; chỉ rút ngắn (R11). */
    suspend fun end(recurringId: String, lastDate: LocalDate): AppResult<Recurring>

    /** Gỡ chặn R13 bằng cách bỏ các kỳ bị chặn: không sinh trước hôm nay nữa. */
    suspend fun skipBlocked(recurringId: String): AppResult<Recurring>

    /** Gỡ chặn R13 và ghi bù các kỳ bị chặn (còn trong 90 ngày) thành nháp. */
    suspend fun backfillBlocked(recurringId: String): AppResult<Recurring>

    suspend fun delete(recurringId: String): AppResult<Unit>

    /**
     * Ghi một kỳ trong **một** transaction DB: revision của khoản vẫn là [expectedRevision], id chưa có (kể cả đã xoá),
     * ví/danh mục còn dùng được (RS13). Không bao giờ ném lỗi: lỗi lưu trữ trả [OccurrenceResult.STALE].
     */
    suspend fun recordOccurrence(
        recurring: Recurring,
        occurrence: ScheduledOccurrence,
        status: OccurrenceStatus,
    ): OccurrenceResult
}
