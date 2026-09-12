package com.topjohnwu.magisk.arch

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.use
import androidx.core.view.WindowCompat
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.snackbar.Snackbar
import com.topjohnwu.magisk.BR
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.base.ActivityExtension
import com.topjohnwu.magisk.core.base.IActivityExtension
import com.topjohnwu.magisk.core.isRunningAsStub
import com.topjohnwu.magisk.core.ktx.reflectField
import com.topjohnwu.magisk.core.wrap
import com.topjohnwu.magisk.ui.theme.Theme
import rikka.insets.WindowInsetsHelper
import rikka.layoutinflater.view.LayoutInflaterFactory

/** Global Metro display-density scale applied to every activity (see [UIActivity.attachBaseContext]). */
const val METRO_DENSITY_SCALE = 0.75f

abstract class UIActivity<Binding : ViewDataBinding>
    : AppCompatActivity(), ViewModelHolder, IActivityExtension {

    protected lateinit var binding: Binding
    protected abstract val layoutRes: Int
    override val extension = ActivityExtension(this)

    protected val binded get() = ::binding.isInitialized

    open val snackbarView get() = binding.root
    open val snackbarAnchorView: View? get() = null

    init {
        AppCompatDelegate.setDefaultNightMode(Config.darkTheme)
    }

    override fun attachBaseContext(base: Context) {
        // Windows Phone Metro look: globally scale down the display density so every
        // screen (View and Compose alike) renders at 0.75x, matching the desired Metro
        // visual scale without requiring a system-wide DPI change.
        val wrapped = base.wrap()
        val config = Configuration(wrapped.resources.configuration)
        config.densityDpi = (config.densityDpi * METRO_DENSITY_SCALE).toInt()
        super.attachBaseContext(wrapped.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the Material 3 dynamic-color overlay directly: the library's own gate skips
        // many Android 12 devices via an OEM whitelist, which left dialogs, selectors and
        // other attr-driven views on the packaged default blue instead of wallpaper colors.
        if (Theme.selected == Theme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setTheme(com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_DayNight)
        }
        layoutInflater.factory2 = LayoutInflaterFactory(delegate)
            .addOnViewCreatedListener(WindowInsetsHelper.LISTENER)

        extension.onCreate(savedInstanceState)
        if (isRunningAsStub) {
            // Overwrite private members to avoid nasty "false" stack traces being logged
            val delegate = delegate
            val clz = delegate.javaClass
            clz.reflectField("mActivityHandlesConfigFlagsChecked").set(delegate, true)
            clz.reflectField("mActivityHandlesConfigFlags").set(delegate, 0)
        }

        super.onCreate(savedInstanceState)

        startObserveLiveData()

        // We need to set the window background explicitly since for whatever reason it's not
        // propagated upstream
        obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
            .use { it.getDrawable(0) }
            .also { window.setBackgroundDrawable(it) }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window?.decorView?.post {
                // If navigation bar is short enough (gesture navigation enabled), make it transparent
                if ((window.decorView.rootWindowInsets?.systemWindowInsetBottom
                        ?: 0) < Resources.getSystem().displayMetrics.density * 40) {
                    window.navigationBarColor = Color.TRANSPARENT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        window.navigationBarDividerColor = Color.TRANSPARENT
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                        window.isStatusBarContrastEnforced = false
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        extension.onSaveInstanceState(outState)
    }

    fun setContentView() {
        binding = DataBindingUtil.setContentView<Binding>(this, layoutRes).also {
            it.setVariable(BR.viewModel, viewModel)
            it.lifecycleOwner = this
        }
    }

    fun setAccessibilityDelegate(delegate: View.AccessibilityDelegate?) {
        binding.root.rootView.accessibilityDelegate = delegate
    }

    fun showSnackbar(
        message: CharSequence,
        length: Int = Snackbar.LENGTH_SHORT,
        builder: Snackbar.() -> Unit = {}
    ) = Snackbar.make(snackbarView, message, length)
        .setAnchorView(snackbarAnchorView).apply(builder).show()

    override fun onResume() {
        super.onResume()
        viewModel.let {
            if (it is AsyncLoadViewModel)
                it.startLoading()
        }
    }

    override fun onEventDispatched(event: ViewEvent) = when (event) {
        is ContextExecutor -> event(this)
        is ActivityExecutor -> event(this)
        else -> Unit
    }
}

fun ViewGroup.startAnimations() {
    val transition = AutoTransition()
        .setInterpolator(FastOutSlowInInterpolator())
        .setDuration(400)
    TransitionManager.beginDelayedTransition(
        this,
        transition
    )
}
