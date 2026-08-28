package com.topjohnwu.magisk.ui.dialog

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.topjohnwu.magisk.ui.theme.MetroColors

/** Small, shared controls for the View-based Metro dialogs. */
object MetroDialogViews {

    fun editText(context: Context, hint: CharSequence? = null, value: CharSequence? = null) =
        EditText(context).apply {
            this.hint = hint
            setText(value)
            setSingleLine(true)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(MetroColors.themeOnSurface(context))
            setHintTextColor(MetroColors.themeOnSurfaceVariant(context))
            backgroundTintList = ColorStateList.valueOf(MetroColors.themePrimary(context))
            minHeight = dp(context, 48)
            setPadding(dp(context, 4), 0, dp(context, 4), 0)
        }

    fun button(context: Context, text: CharSequence) = MaterialButton(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        minHeight = dp(context, 44)
        minWidth = dp(context, 72)
        setPadding(dp(context, 12), 0, dp(context, 12), 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(MetroColors.themePrimary(context))
        setTypeface(typeface, Typeface.BOLD)
        cornerRadius = 0
        elevation = 0f
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        rippleColor = ColorStateList.valueOf(MetroColors.softThemePrimary(context))
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(context, 48),
        )
    }

    fun styleLabel(view: TextView, sizeSp: Float = 14f) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }

    private fun dp(context: Context, value: Int) =
        (value * context.resources.displayMetrics.density).toInt()
}