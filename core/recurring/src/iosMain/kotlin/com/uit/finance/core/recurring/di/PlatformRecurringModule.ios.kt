package com.uit.finance.core.recurring.di

import org.koin.core.module.Module
import org.koin.dsl.module

/** Thông báo đi qua core/notification; iOS không có worker riêng. */
internal actual fun platformRecurringModule(): Module = module {}

/**
 * iOS không có worker hằng ngày riêng: BGAppRefreshTask của sync (core/sync) đánh thức app định kỳ, coordinator sinh kỳ
 * sau mỗi vòng sync xong; mở app lên foreground luôn bù đủ.
 */
internal actual fun scheduleDailyGeneration(koin: org.koin.core.Koin) = Unit
