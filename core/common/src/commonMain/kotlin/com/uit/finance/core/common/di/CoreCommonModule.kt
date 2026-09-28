package com.uit.finance.core.common.di

import co.touchlab.kermit.Logger
import com.uit.finance.core.common.coroutines.DefaultDispatcherProvider
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.id.RandomUuidGenerator
import com.uit.finance.core.common.id.UuidGenerator
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.common.time.SystemClock
import org.koin.dsl.module

val coreCommonModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single<UuidGenerator> { RandomUuidGenerator() }
    single<Clock> { SystemClock }
    single<Logger> { Logger.withTag("Finance") }
}
