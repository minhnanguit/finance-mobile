package com.uit.finance.feature.recurring.di

import com.uit.finance.feature.recurring.domain.usecase.ConfirmOccurrenceUseCase
import com.uit.finance.feature.recurring.domain.usecase.CreateRecurringUseCase
import com.uit.finance.feature.recurring.domain.usecase.DeleteRecurringUseCase
import com.uit.finance.feature.recurring.domain.usecase.EndRecurringUseCase
import com.uit.finance.feature.recurring.domain.usecase.ObserveRecurringFormDataUseCase
import com.uit.finance.feature.recurring.domain.usecase.ObserveRecurringOverviewUseCase
import com.uit.finance.feature.recurring.domain.usecase.RequestReminderPermissionUseCase
import com.uit.finance.feature.recurring.domain.usecase.ResolveBlockedRecurringUseCase
import com.uit.finance.feature.recurring.domain.usecase.SkipOccurrenceUseCase
import com.uit.finance.feature.recurring.domain.usecase.UpdateRecurringUseCase
import com.uit.finance.feature.recurring.presentation.editor.RecurringEditorViewModel
import com.uit.finance.feature.recurring.presentation.list.RecurringListViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Cần trong graph: repository của `coreRecurringModule` + `coreLedgerModule`, `NotificationPermissionGate`
 * (core/recurring), use case sync của `coreSyncModule`, `MoneyFormatter`, `Clock`.
 */
val recurringFeatureModule: Module = module {
    // domain
    factoryOf(::ObserveRecurringOverviewUseCase)
    factoryOf(::ObserveRecurringFormDataUseCase)
    factoryOf(::CreateRecurringUseCase)
    factoryOf(::UpdateRecurringUseCase)
    factoryOf(::EndRecurringUseCase)
    factoryOf(::DeleteRecurringUseCase)
    factoryOf(::ResolveBlockedRecurringUseCase)
    factoryOf(::ConfirmOccurrenceUseCase)
    factoryOf(::SkipOccurrenceUseCase)
    factoryOf(::RequestReminderPermissionUseCase)

    // presentation
    viewModelOf(::RecurringListViewModel)
    viewModel { params ->
        RecurringEditorViewModel(
            args = params.get(),
            observeFormData = get(),
            createRecurring = get(),
            updateRecurring = get(),
            endRecurring = get(),
            deleteRecurring = get(),
            requestReminderPermission = get(),
        )
    }
}
