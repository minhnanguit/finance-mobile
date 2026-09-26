package com.mosaicglobal.finance.core.auth.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Mở Custom Tab rồi đợi một trong hai: redirect quay về ([onNewIntent]), hoặc user đóng browser
 * (quay lại [onResume] mà không có redirect). `public` vì Android framework instantiate nó.
 */
class AuthorizationActivity : Activity() {

    private var browserOpened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        browserOpened = savedInstanceState?.getBoolean(KEY_BROWSER_OPENED) ?: false
        val redirect = intent?.data
        when {
            // Instance mới được tạo thẳng bằng redirect (instance cũ đã mất).
            redirect != null -> finishWith(LaunchResult.Redirected(redirect.toString()))
            // Process đã chết khi user ở browser: không còn coroutine nào chờ kết quả.
            savedInstanceState != null && !AuthorizationFlowBridge.isPending -> finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        finishWith(intent.data?.let { LaunchResult.Redirected(it.toString()) } ?: LaunchResult.Cancelled)
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        if (!browserOpened) {
            browserOpened = true
            openBrowser()
        } else {
            finishWith(LaunchResult.Cancelled)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_BROWSER_OPENED, browserOpened)
    }

    private fun openBrowser() {
        val url = intent?.getStringExtra(EXTRA_AUTHORIZATION_URL)
        if (url == null) {
            finishWith(LaunchResult.Failed("thiếu authorization URL"))
            return
        }
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, Uri.parse(url))
        } catch (e: ActivityNotFoundException) {
            finishWith(LaunchResult.Failed("máy không có browser nào"))
        }
    }

    private fun finishWith(result: LaunchResult) {
        AuthorizationFlowBridge.complete(result)
        finish()
    }

    internal companion object {
        private const val EXTRA_AUTHORIZATION_URL = "com.mosaicglobal.finance.core.auth.AUTHORIZATION_URL"
        private const val KEY_BROWSER_OPENED = "browser_opened"

        /** Gọi từ application context nên cần NEW_TASK. */
        fun startIntent(context: Context, authorizationUrl: String): Intent =
            Intent(context, AuthorizationActivity::class.java)
                .putExtra(EXTRA_AUTHORIZATION_URL, authorizationUrl)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** CLEAR_TOP gỡ Custom Tab khỏi back stack, SINGLE_TOP giao redirect vào [onNewIntent]. */
        fun redirectIntent(context: Context, redirect: Uri?): Intent =
            Intent(context, AuthorizationActivity::class.java)
                .setData(redirect)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
