package com.uit.finance.core.sync.domain.usecase

import com.uit.finance.core.sync.issue.SyncIssueRepository

/** User đã xem thông báo "thay đổi bị từ chối": ẩn badge cho tới khi có lần từ chối mới. */
class AcknowledgeSyncIssuesUseCase(private val issues: SyncIssueRepository) {
    suspend operator fun invoke() = issues.acknowledgeAll()
}
