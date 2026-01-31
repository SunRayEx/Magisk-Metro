package com.magiskube.magisk.dialog

import android.app.ProgressDialog
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.magiskube.magisk.arch.NavigationActivity
import com.magiskube.magisk.arch.UIActivity
import com.magiskube.magisk.core.R
import com.magiskube.magisk.core.ktx.toast
import com.magiskube.magisk.core.tasks.MagiskInstaller
import com.magiskube.magisk.events.DialogBuilder
import com.magiskube.magisk.ui.flash.FlashFragment
import com.magiskube.magisk.view.MagiskDialog
import kotlinx.coroutines.launch

class UninstallDialog : DialogBuilder {

    override fun build(dialog: MagiskDialog) {
        dialog.apply {
            setTitle(R.string.uninstall_magisk_title)
            setMessage(R.string.uninstall_magisk_msg)
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = R.string.restore_img
                onClick { restore(dialog.activity) }
            }
            setButton(MagiskDialog.ButtonType.NEGATIVE) {
                text = R.string.complete_uninstall
                onClick { completeUninstall(dialog) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun restore(activity: UIActivity<*>) {
        val dialog = ProgressDialog(activity).apply {
            setMessage(activity.getString(R.string.restore_img_msg))
            show()
        }

        activity.lifecycleScope.launch {
            MagiskInstaller.Restore().exec { success ->
                dialog.dismiss()
                if (success) {
                    activity.toast(R.string.restore_done, Toast.LENGTH_SHORT)
                } else {
                    activity.toast(R.string.restore_fail, Toast.LENGTH_LONG)
                }
            }
        }
    }

    private fun completeUninstall(dialog: MagiskDialog) {
        (dialog.ownerActivity as NavigationActivity<*>)
            .navigation.navigate(FlashFragment.uninstall())
    }

}
