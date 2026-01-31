package com.MagisKube.magisk.dialog

import com.MagisKube.magisk.core.AppContext
import com.MagisKube.magisk.core.Info
import com.MagisKube.magisk.core.R
import com.MagisKube.magisk.core.download.DownloadEngine
import com.MagisKube.magisk.core.download.Subject
import com.MagisKube.magisk.view.MagiskDialog
import java.io.File

class ManagerInstallDialog : MarkDownDialog() {

    override suspend fun getMarkdownText(): String {
        val text = Info.update.note
        // Cache the changelog
        File(AppContext.cacheDir, "${Info.update.versionCode}.md").writeText(text)
        return text
    }

    override fun build(dialog: MagiskDialog) {
        super.build(dialog)
        dialog.apply {
            setCancelable(true)
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = R.string.install
                onClick { DownloadEngine.startWithActivity(activity, Subject.App()) }
            }
            setButton(MagiskDialog.ButtonType.NEGATIVE) {
                text = android.R.string.cancel
            }
        }
    }

}
