package com.magiskube.magisk.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.widget.CompoundButtonCompat

class IndeterminateCheckBox @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatCheckBox(context, attrs, defStyleAttr) {

    private var _state: Boolean? = null
    private var onStateChangedListener: OnStateChangedListener? = null

    interface OnStateChangedListener {
        fun onStateChanged(view: IndeterminateCheckBox, state: Boolean?)
    }

    var state: Boolean?
        get() = _state
        set(value) {
            if (_state != value) {
                _state = value
                updateButtonState()
                onStateChangedListener?.onStateChanged(this, value)
            }
        }

    fun setOnStateChangedListener(listener: OnStateChangedListener?) {
        this.onStateChangedListener = listener
    }

    init {
        buttonDrawable = null
        updateButtonState()
    }

    private fun updateButtonState() {
        when (_state) {
            true -> isChecked = true
            false -> isChecked = false
            null -> {
                isChecked = false
                CompoundButtonCompat.setButtonTintList(this, null)
            }
        }
    }
}
