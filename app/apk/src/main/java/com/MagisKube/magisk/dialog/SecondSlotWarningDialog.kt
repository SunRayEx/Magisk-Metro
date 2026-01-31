package com.MagisKube.magisk.dialog

import com.MagisKube.magisk.core.R
import com.MagisKube.magisk.events.DialogBuilder
import com.MagisKube.magisk.view.MagiskDialog

class SecondSlotWarningDialog : DialogBuilder {

    override fun build(dialog: MagiskDialog) {
        dialog.apply {
            setTitle(android.R.string.dialog_alert_title)
            setMessage(R.string.install_inactive_slot_msg)
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = android.R.string.ok
            }
            setCancelable(true)
        }
    }
}
