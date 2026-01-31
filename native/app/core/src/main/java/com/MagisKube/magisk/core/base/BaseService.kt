package com.MagisKube.magisk.core.base

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.MagisKube.magisk.core.patch

open class BaseService : Service() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.patch())
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
