package com.uit.finance.feature.capture.di

import com.uit.finance.feature.capture.domain.usecase.ConfirmDraftUseCase
import com.uit.finance.feature.capture.domain.usecase.DismissCaptureEventUseCase
import com.uit.finance.feature.capture.domain.usecase.DismissDraftUseCase
import com.uit.finance.feature.capture.domain.usecase.GetListenerAccessUseCase
import com.uit.finance.feature.capture.domain.usecase.LinkCaptureAccountUseCase
import com.uit.finance.feature.capture.domain.usecase.ObserveCaptureInboxUseCase
import com.uit.finance.feature.capture.domain.usecase.ObserveCaptureSetupUseCase
import com.uit.finance.feature.capture.domain.usecase.ObservePasteFormDataUseCase
import com.uit.finance.feature.capture.domain.usecase.OpenCaptureInboxUseCase
import com.uit.finance.feature.capture.domain.usecase.OpenListenerSettingsUseCase
import com.uit.finance.feature.capture.domain.usecase.ParsePastedMessageUseCase
import com.uit.finance.feature.capture.domain.usecase.SavePastedMessageUseCase
import com.uit.finance.feature.capture.domain.usecase.SetCaptureBanksUseCase
import com.uit.finance.feature.capture.domain.usecase.SetCaptureEnabledUseCase
import com.uit.finance.feature.capture.domain.usecase.SetDefaultCategoryUseCase
import com.uit.finance.feature.capture.presentation.inbox.CaptureInboxViewModel
import com.uit.finance.feature.capture.presentation.paste.PasteMessageViewModel
import com.uit.finance.feature.capture.presentation.setup.CaptureSetupViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Cần trong graph: repository của `coreCaptureModule` + `coreLedgerModule`, `NotificationAccess` (core/notification),
 * `MoneyFormatter` (core/presentation).
 */
val captureFeatureModule: Module = module {
    // domain
    factoryOf(::ObserveCaptureSetupUseCase)
    factoryOf(::SetCaptureEnabledUseCase)
    factoryOf(::SetCaptureBanksUseCase)
    factoryOf(::SetDefaultCategoryUseCase)
    factoryOf(::GetListenerAccessUseCase)
    factoryOf(::OpenListenerSettingsUseCase)
    factoryOf(::ObserveCaptureInboxUseCase)
    factoryOf(::OpenCaptureInboxUseCase)
    factoryOf(::ConfirmDraftUseCase)
    factoryOf(::DismissDraftUseCase)
    factoryOf(::DismissCaptureEventUseCase)
    factoryOf(::LinkCaptureAccountUseCase)
    factoryOf(::ObservePasteFormDataUseCase)
    factoryOf(::ParsePastedMessageUseCase)
    factoryOf(::SavePastedMessageUseCase)

    // presentation
    viewModelOf(::CaptureSetupViewModel)
    viewModelOf(::CaptureInboxViewModel)
    viewModelOf(::PasteMessageViewModel)
}
