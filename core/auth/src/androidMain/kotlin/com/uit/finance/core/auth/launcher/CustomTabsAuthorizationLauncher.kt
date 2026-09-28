package com.uit.finance.core.auth.launcher

import android.content.Context

internal class CustomTabsAuthorizationLauncher(private val context: Context) : AuthorizationLauncher {

    override suspend fun launch(authorizationUrl: String, redirectUri: String): LaunchResult =
        AuthorizationFlowBridge.run {
            context.startActivity(AuthorizationActivity.startIntent(context, authorizationUrl))
        }
}
