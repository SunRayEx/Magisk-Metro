package com.MagisKube.magisk.ui.surequest

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import com.MagisKube.magisk.R
import com.MagisKube.magisk.arch.UIActivity
import com.MagisKube.magisk.arch.viewModel
import com.MagisKube.magisk.core.base.UntrackedActivity
import com.MagisKube.magisk.core.su.SuCallbackHandler
import com.MagisKube.magisk.core.su.SuCallbackHandler.REQUEST
import com.MagisKube.magisk.databinding.ActivityRequestBinding
import com.MagisKube.magisk.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class SuRequestActivity : UIActivity<ActivityRequestBinding>(), UntrackedActivity {

    override val layoutRes: Int = R.layout.activity_request
    override val viewModel: SuRequestViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }
        setTheme(Theme.selected.themeRes)
        super.onCreate(savedInstanceState)

        if (intent.action == Intent.ACTION_VIEW) {
            val action = intent.getStringExtra("action")
            if (action == REQUEST) {
                viewModel.handleRequest(intent)
            } else {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        SuCallbackHandler.run(this@SuRequestActivity, action, intent.extras)
                    }
                    finish()
                }
            }
        } else {
            finish()
        }
    }

    override fun getTheme(): Resources.Theme {
        val theme = super.getTheme()
        theme.applyStyle(R.style.Foundation_Floating, true)
        return theme
    }

    override fun onBackPressed() {
        viewModel.denyPressed()
    }

    override fun finish() {
        super.finishAndRemoveTask()
    }
}
