package com.mosaicglobal.finance.core.network.di

import com.mosaicglobal.finance.core.network.api.UserApi
import com.mosaicglobal.finance.core.network.api.internal.GeneratedUserApiAdapter
import com.mosaicglobal.finance.core.network.auth.TokenCache
import com.mosaicglobal.finance.core.network.client.KtorTokenCache
import com.mosaicglobal.finance.core.network.client.NetworkConfig
import com.mosaicglobal.finance.core.network.client.createHttpClient
import com.mosaicglobal.finance.core.network.client.defaultJson
import com.mosaicglobal.finance.core.network.client.platformHttpEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Cần có trong graph: [NetworkConfig] (app), `TokenProvider` (core/datastore), `TokenRefresher`
 * (core/auth), `UuidGenerator` + Kermit `Logger` (core/common).
 */
val coreNetworkModule: Module = module {
    single<Json> { defaultJson() }
    single<HttpClientEngine> { platformHttpEngine() }
    single<HttpClient> {
        createHttpClient(
            engine = get(),
            config = get(),
            json = get(),
            tokenProvider = get(),
            tokenRefresher = get(),
            uuidGenerator = get(),
            logger = get(),
        )
    }
    single<TokenCache> { KtorTokenCache(get()) }
    single<UserApi> { GeneratedUserApiAdapter(baseUrl = get<NetworkConfig>().baseUrl, httpClient = get()) }
}
