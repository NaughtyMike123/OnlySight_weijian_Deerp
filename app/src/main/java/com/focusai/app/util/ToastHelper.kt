package com.focusai.app.util

import android.content.Context
import android.widget.Toast
import com.focusai.app.R

object ToastHelper {
    fun showInterceptToast(context: Context) {
        Toast.makeText(
            context.applicationContext,
            context.getString(R.string.intercept_toast),
            Toast.LENGTH_LONG
        ).show()
    }
}
