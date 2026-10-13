package com.tacorian.fireplace.data

import android.content.Context
import androidx.core.content.edit
import com.tacorian.fireplace.BuildConfig

/** The Proflame module's address: the one saved in Settings, else the build default (may be blank). */
object FireplaceHost {
  private const val PREFS = "fireplace"
  private const val KEY = "host"

  fun get(context: Context): String =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: BuildConfig.DEFAULT_HOST

  fun set(context: Context, host: String) =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, host) }
}
