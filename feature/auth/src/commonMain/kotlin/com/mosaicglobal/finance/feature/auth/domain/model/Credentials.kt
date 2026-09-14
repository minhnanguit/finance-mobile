package com.mosaicglobal.finance.feature.auth.domain.model

data class Credentials(
    val email: String,
    val password: String,
)

data class Registration(
    val email: String,
    val password: String,
    val displayName: String,
)
