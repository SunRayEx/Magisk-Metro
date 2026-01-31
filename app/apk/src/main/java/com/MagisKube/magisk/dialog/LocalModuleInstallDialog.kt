package com.MagisKube.magisk.dialog

import android.net.Uri
import com.MagisKube.magisk.MainDirections
import com.MagisKube.magisk.core.Const
import com.MagisKube.magisk.core.R
import com.MagisKube.magisk.events.DialogBuilder
import com.MagisKube.magisk.ui.module.ModuleViewModel
import com.MagisKube.magisk.view.MagiskDialog

class LocalModuleInstallDialog(
    private val viewModel: ModuleViewModel,
    private val uri: Uri,
    private val displayName: String
) : DialogBuilder {
    override fun build(dialog: MagiskDialog) {
        dialog.apply {
            setTitle(R.string.confirm_install_title)
            setMessage(context.getString(R.string.confirm_install, displayName))
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = android.R.string.ok
                onClick {
                    viewModel.apply {
                        MainDirections.actionFlashFragment(Const.Value.FLASH_ZIP, uri).navigate()
                    }
                }
            }
            setButton(MagiskDialog.ButtonType.NEGATIVE) {
                text = android.R.string.cancel
            }
        }
    }
}
