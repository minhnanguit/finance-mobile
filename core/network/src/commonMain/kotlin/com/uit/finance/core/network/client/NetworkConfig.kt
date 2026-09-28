package com.uit.finance.core.network.client

data class NetworkConfig(
    /** e.g. `http://10.0.2.2:8080` on the Android emulator, `http://localhost:8080` on the iOS simulator. */
    val baseUrl: String,
    val connectTimeoutMillis: Long = 10_000,
    val requestTimeoutMillis: Long = 30_000,
    val socketTimeoutMillis: Long = 30_000,
    /** Log request/response lines (headers are sanitised, bodies are never logged). */
    val logHttp: Boolean = false,
) {
    init {
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) { "baseUrl must be absolute: $baseUrl" }
    }
}
