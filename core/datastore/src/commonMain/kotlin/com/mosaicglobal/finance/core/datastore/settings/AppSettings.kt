package com.mosaicglobal.finance.core.datastore.settings

import com.mosaicglobal.finance.core.common.id.UuidGenerator
import com.russhwolf.settings.Settings

/**
 * Non-secret preferences (multiplatform-settings: SharedPreferences / NSUserDefaults).
 * Anything sensitive belongs in [com.mosaicglobal.finance.core.datastore.secure.SecureStorage].
 */
class AppSettings(
    private val settings: Settings,
    private val uuidGenerator: UuidGenerator,
) {
    /** Stable installation id generated on first launch; sent as `device.deviceId`. */
    val installationId: String
        get() = settings.getStringOrNull(KEY_INSTALLATION_ID) ?: uuidGenerator.generate().also {
            settings.putString(KEY_INSTALLATION_ID, it)
        }

    var onboardingCompleted: Boolean
        get() = settings.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = settings.putBoolean(KEY_ONBOARDING_COMPLETED, value)

    var lastSuccessfulSyncEpochSeconds: Long?
        get() = settings.getLongOrNull(KEY_LAST_SYNC)
        set(value) = if (value == null) settings.remove(KEY_LAST_SYNC) else settings.putLong(KEY_LAST_SYNC, value)

    private companion object {
        const val KEY_INSTALLATION_ID = "installation_id"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_LAST_SYNC = "last_successful_sync"
    }
}
