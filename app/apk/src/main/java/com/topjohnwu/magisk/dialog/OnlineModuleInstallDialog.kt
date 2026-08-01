package com.topjohnwu.magisk.dialog

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import com.topjohnwu.magisk.core.R
import com.topjohnwu.magisk.core.di.ServiceLocator
import com.topjohnwu.magisk.core.download.DownloadEngine
import com.topjohnwu.magisk.core.download.Subject
import com.topjohnwu.magisk.core.model.module.OnlineModule
import com.topjohnwu.magisk.ui.flash.FlashFragment
import com.topjohnwu.magisk.view.MagiskDialog
import com.topjohnwu.magisk.view.Notifications
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize

class OnlineModuleInstallDialog(private val item: OnlineModule) : MarkDownDialog() {

    private val svc get() = ServiceLocator.networkService

    override suspend fun getMarkdownText(): String {
        val str = svc.fetchString(item.changelog)
        return if (str.length > 1000) str.substring(0, 1000) else str
    }

    @Parcelize
    class Module(
        override val module: OnlineModule,
        override val autoLaunch: Boolean,
        override val notifyId: Int = Notifications.nextId()
    ) : Subject.Module() {
        override fun pendingIntent(context: Context) = FlashFragment.installIntent(context, file)
    }

    override fun build(dialog: MagiskDialog) {
        dialog.apply {

            fun download(install: Boolean) {
                DownloadEngine.startWithActivity(activity, Module(item, install))
            }

            val title = context.getString(R.string.repo_install_title,
                item.name, item.version, item.versionCode)

            setTitle(title)
            setCancelable(true)
            // A flat, accent-led content block deliberately avoids Material card affordances and
            // mirrors the typographic, rectangular Windows 8 Metro dialog language.
            val padding = (24 * context.resources.displayMetrics.density).toInt()
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(ContextCompat.getColor(context, com.topjohnwu.magisk.R.color.metro_accent_modules))
                setPadding(padding, padding, padding, padding)
            }
            val version = TextView(context).apply {
                text = "${item.name}\n${item.version} (${item.versionCode})"
                setTextColor(ContextCompat.getColor(context, android.R.color.black))
                textSize = 22f
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            }
            val changelog = TextView(context).apply {
                text = context.getString(com.topjohnwu.magisk.R.string.metro_loading)
                setTextColor(ContextCompat.getColor(context, android.R.color.black))
                textSize = 14f
                gravity = Gravity.START
                setPadding(0, padding / 2, 0, 0)
            }
            content.addView(version)
            content.addView(changelog)
            setView(content)
            activity.lifecycleScope.launch {
                val markdown = runCatching {
                    withContext(Dispatchers.IO) { getMarkdownText() }
                }.getOrElse { context.getString(com.topjohnwu.magisk.core.R.string.download_file_error) }
                ServiceLocator.markwon.setMarkdown(changelog, markdown)
            }
            setButton(MagiskDialog.ButtonType.NEGATIVE) {
                text = R.string.download
                onClick { download(false) }
            }
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = R.string.install
                onClick { download(true) }
            }
            setButton(MagiskDialog.ButtonType.NEUTRAL) {
                text = android.R.string.cancel
            }
        }
    }

}
