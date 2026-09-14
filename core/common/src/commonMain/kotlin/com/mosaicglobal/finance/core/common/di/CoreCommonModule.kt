package com.mosaicglobal.finance.core.common.di

import co.touchlab.kermit.Logger
import com.mosaicglobal.finance.core.common.coroutines.DefaultDispatcherProvider
import com.mosaicglobal.finance.core.common.coroutines.DispatcherProvider
import com.mosaicglobal.finance.core.common.id.RandomUuidGenerator
import com.mosaicglobal.finance.core.common.id.UuidGenerator
import com.mosaicglobal.finance.core.common.time.Clock
import com.mosaicglobal.finance.core.common.time.SystemClock
import org.koin.dsl.module

val coreCommonModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single<UuidGenerator> { RandomUuidGenerator() }
    single<Clock> { SystemClock }
    single<Logger> { Logger.withTag("Finance") }
}
