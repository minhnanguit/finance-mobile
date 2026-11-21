package com.uit.finance.core.capture.data.settings

import com.russhwolf.settings.Settings
import com.uit.finance.core.capture.domain.model.BankId
import com.uit.finance.core.capture.domain.model.CaptureEnvironment
import com.uit.finance.core.capture.domain.model.CaptureSettings
import com.uit.finance.core.capture.domain.repository.CaptureSettingsRepository
import com.uit.finance.core.session.LocalDataState
import com.uit.finance.core.session.UserDataCleaner
import com.uit.finance.core.session.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Cài đặt capture theo user, cùng khuôn `ReminderSettingsRepositoryImpl` (plan mục 3.6). Không sync (B: cài đặt
 * đọc thông báo là của máy này).
 *
 * Ngoài key theo user còn hai key **cấp máy** để listener lọc nhanh mà không cần sổ mở (L9): hợp package và tên
 * người gửi SMS của mọi user trên máy đã bật. Tính lại mỗi khi đổi cài đặt và khi xoá user.
 */
internal class CaptureSettingsRepositoryImpl(
    private val settings: Settings,
    private val session: UserSession,
    private val deviceKeys: CaptureDeviceKeys,
) : CaptureSettingsRepository {
    private val version = MutableStateFlow(0L)

    override fun observe(): Flow<CaptureSettings> = combine(session.state, version) { state, _ ->
        val userId = (state as? LocalDataState.Ready)?.userId ?: return@combine CaptureSettings.DEFAULT
        read(userId)
    }.distinctUntilChanged()

    fun read(userId: String): CaptureSettings = deviceKeys.read(userId)

    override suspend fun setEnabled(enabled: Boolean) = edit { userId -> settings.putBoolean(enabledKey(userId), enabled) }

    override suspend fun setBanks(banks: Set<BankId>) = edit { userId ->
        settings.putString(banksKey(userId), banks.filter { it in availableBanks() }.joinToString(",") { it.name })
    }

    override suspend fun setDefaultExpenseCategory(categoryId: String?) = edit { userId -> putOrRemove(expenseKey(userId), categoryId) }

    override suspend fun setDefaultIncomeCategory(categoryId: String?) = edit { userId -> putOrRemove(incomeKey(userId), categoryId) }

    override fun availableBanks(): List<BankId> = deviceKeys.availableBanks()

    fun devicePackages(): Set<String> = deviceKeys.devicePackages()

    fun deviceSmsSenders(): Set<String> = deviceKeys.deviceSmsSenders()

    private inline fun edit(block: (String) -> Unit) {
        val userId = (session.state.value as? LocalDataState.Ready)?.userId ?: return
        block(userId)
        deviceKeys.recompute()
        version.value += 1
    }

    private fun putOrRemove(key: String, value: String?) {
        if (value == null) settings.remove(key) else settings.putString(key, value)
    }

    internal companion object {
        const val PREFIX = "capture."
        const val ENABLED_SUFFIX = ".enabled"
        const val DEVICE_PACKAGES = "capture.device_packages"
        const val DEVICE_SENDERS = "capture.device_senders"
        fun enabledKey(userId: String) = "$PREFIX$userId$ENABLED_SUFFIX"
        fun banksKey(userId: String) = "$PREFIX$userId.banks"
        fun expenseKey(userId: String) = "$PREFIX$userId.default_expense_category"
        fun incomeKey(userId: String) = "$PREFIX$userId.default_income_category"
        fun userKeys(userId: String) = listOf(enabledKey(userId), banksKey(userId), expenseKey(userId), incomeKey(userId))
    }
}

/**
 * Đọc cài đặt theo user và tính hai key **cấp máy** cho listener. Chỉ cần `Settings` + môi trường, **không** cần
 * `UserSession`: [CaptureUserDataCleaner] dùng nó, mà `UserSession` lại lấy mọi `UserDataCleaner` — phụ thuộc
 * `UserSession` ở đây sẽ thành vòng tròn trong Koin (crash lúc khởi động).
 */
internal class CaptureDeviceKeys(
    private val settings: Settings,
    private val environment: CaptureEnvironment,
) {
    fun availableBanks(): List<BankId> = BankId.entries.filter { !it.debugOnly || environment.debug }

    fun read(userId: String): CaptureSettings = CaptureSettings(
        enabled = settings.getBoolean(CaptureSettingsRepositoryImpl.enabledKey(userId), false),
        banks = decodeBanks(settings.getStringOrNull(CaptureSettingsRepositoryImpl.banksKey(userId)))
            .filterTo(mutableSetOf()) { it in availableBanks() },
        defaultExpenseCategoryId = settings.getStringOrNull(CaptureSettingsRepositoryImpl.expenseKey(userId)),
        defaultIncomeCategoryId = settings.getStringOrNull(CaptureSettingsRepositoryImpl.incomeKey(userId)),
    )

    /** Package của app được phép (cấp máy). Gọi từ listener trên main thread: chỉ đọc một key. */
    fun devicePackages(): Set<String> = split(CaptureSettingsRepositoryImpl.DEVICE_PACKAGES)

    fun deviceSmsSenders(): Set<String> = split(CaptureSettingsRepositoryImpl.DEVICE_SENDERS)

    /** Tính lại hai key cấp máy từ cài đặt của mọi user trên máy. */
    fun recompute() {
        val prefix = CaptureSettingsRepositoryImpl.PREFIX
        val suffix = CaptureSettingsRepositoryImpl.ENABLED_SUFFIX
        val userIds = settings.keys.mapNotNullTo(mutableSetOf()) { key ->
            key.takeIf { it.startsWith(prefix) && it.endsWith(suffix) }?.removePrefix(prefix)?.removeSuffix(suffix)
        }
        val banks = userIds.map(::read).filter { it.enabled }.flatMap { it.banks }.toSet()
        val packages = banks.flatMap { it.packages }.toMutableSet()
        val senders = banks.flatMap { it.smsSenders }.toSet()
        if (senders.isNotEmpty()) packages += BankId.SMS_APP_PACKAGES
        settings.putString(CaptureSettingsRepositoryImpl.DEVICE_PACKAGES, packages.sorted().joinToString(","))
        settings.putString(CaptureSettingsRepositoryImpl.DEVICE_SENDERS, senders.sorted().joinToString(","))
    }

    private fun split(key: String): Set<String> =
        settings.getStringOrNull(key).orEmpty().split(',').filterTo(mutableSetOf()) { it.isNotBlank() }

    private fun decodeBanks(raw: String?): Set<BankId> =
        raw.orEmpty().split(',').mapNotNullTo(mutableSetOf()) { BankId.fromName(it.trim()) }
}

/** Đăng xuất / xoá tài khoản: xoá cài đặt capture của user đó rồi tính lại danh sách cấp máy (S5). */
internal class CaptureUserDataCleaner(
    private val settings: Settings,
    private val deviceKeys: CaptureDeviceKeys,
) : UserDataCleaner {
    override suspend fun clear(userId: String) {
        CaptureSettingsRepositoryImpl.userKeys(userId).forEach(settings::remove)
        deviceKeys.recompute()
    }
}
