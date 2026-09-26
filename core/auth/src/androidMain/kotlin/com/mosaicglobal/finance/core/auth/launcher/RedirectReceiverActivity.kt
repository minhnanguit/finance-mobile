package com.mosaicglobal.finance.core.auth.launcher

import android.app.Activity
import android.os.Bundle

/**
 * Nhận redirect URI từ browser rồi chuyển ngay cho [AuthorizationActivity]. App khai báo activity này
 * kèm intent-filter cho scheme của mình trong AndroidManifest. `public` vì Android framework
 * instantiate nó.
 */
class RedirectReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(AuthorizationActivity.redirectIntent(this, intent?.data))
        finish()
    }
}
