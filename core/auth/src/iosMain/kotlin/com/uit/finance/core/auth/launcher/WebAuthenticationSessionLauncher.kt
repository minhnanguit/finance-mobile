package com.uit.finance.core.auth.launcher

import com.uit.finance.core.common.coroutines.DispatcherProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorDomain
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

internal class WebAuthenticationSessionLauncher(
    private val dispatchers: DispatcherProvider,
) : AuthorizationLauncher {

    // `presentationContextProvider` là weak reference phía ObjC: phải giữ strong reference ở đây.
    private val contextProvider = KeyWindowContextProvider()

    override suspend fun launch(authorizationUrl: String, redirectUri: String): LaunchResult =
        withContext(dispatchers.main) {
            suspendCancellableCoroutine { continuation ->
                val url = NSURL.URLWithString(authorizationUrl)
                if (url == null) {
                    continuation.resume(LaunchResult.Failed("authorization URL không hợp lệ"))
                    return@suspendCancellableCoroutine
                }
                val session = ASWebAuthenticationSession(url, redirectUri.substringBefore(':')) { callback, error ->
                    if (!continuation.isActive) return@ASWebAuthenticationSession
                    val result = when {
                        callback != null -> LaunchResult.Redirected(callback.absoluteString.orEmpty())
                        error?.domain == ASWebAuthenticationSessionErrorDomain &&
                            error?.code == ASWebAuthenticationSessionErrorCodeCanceledLogin -> LaunchResult.Cancelled
                        else -> LaunchResult.Failed(error?.localizedDescription)
                    }
                    continuation.resume(result)
                }
                session.presentationContextProvider = contextProvider
                // Ephemeral: không chia sẻ cookie với Safari, nên không có hộp thoại xin phép của iOS
                // và logout không để lại SSO session nào trong browser.
                session.prefersEphemeralWebBrowserSession = true
                if (!session.start() && continuation.isActive) {
                    continuation.resume(LaunchResult.Failed("ASWebAuthenticationSession không start được"))
                }
                continuation.invokeOnCancellation {
                    dispatch_async(dispatch_get_main_queue()) { session.cancel() }
                }
            }
        }
}

private class KeyWindowContextProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    @Suppress("DEPRECATION") // keyWindow deprecated từ iOS 13 nhưng vẫn đúng cho app một scene
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
        UIApplication.sharedApplication.keyWindow ?: UIWindow()
}
