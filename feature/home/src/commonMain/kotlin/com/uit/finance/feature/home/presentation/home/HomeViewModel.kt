package com.uit.finance.feature.home.presentation.home

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.feature.home.domain.usecase.ObserveHomeDashboardUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
internal class HomeViewModel(
    observeDashboard: ObserveHomeDashboardUseCase,
    private val mapper: HomeUiMapper,
    private val clock: Clock,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    /** Ngày đang hiển thị. Đổi ngày (qua đêm, qua tháng) là query lại kỳ "tháng này" và nhãn "Hôm nay". */
    private val today = MutableStateFlow<LocalDate>(clock.today())

    init {
        // Flow từ DB trên máy: mỗi giao dịch mới (tự ghi hay sync về) là một bản state mới.
        today
            .flatMapLatest { date -> observeDashboard(date) }
            .onEach { dashboard -> setState { mapper.map(dashboard, current = this) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: HomeIntent) = when (intent) {
        is HomeIntent.Open -> sendEffect(HomeEffect.Navigate(intent.target))
        HomeIntent.Refresh -> refreshToday()
    }

    /** `StateFlow` bỏ qua giá trị trùng, nên resume trong cùng ngày không query lại. */
    private fun refreshToday() {
        today.value = clock.today()
    }
}
