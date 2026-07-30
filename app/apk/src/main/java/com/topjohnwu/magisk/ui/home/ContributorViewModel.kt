package com.topjohnwu.magisk.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import com.topjohnwu.magisk.arch.BaseViewModel
import com.topjohnwu.magisk.arch.ContextExecutor
import com.topjohnwu.magisk.arch.ViewEvent
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.core.R as CoreR

class ContributorViewModel : BaseViewModel() {

    fun onLinkPressed(link: String) = object : ViewEvent(), ContextExecutor {
        override fun invoke(context: Context) {
            val intent = Intent(Intent.ACTION_VIEW, link.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                context.toast(CoreR.string.open_link_failed_toast, Toast.LENGTH_SHORT)
            }
        }
    }.publish()
}