package com.aloyshf.retrodroid

import android.content.Context
import android.content.res.Configuration
import com.aloyshf.retrodroid.data.Prefs
import java.util.Locale

object AppLocale {
    /** Wraps the base context so resources resolve in the selected app language. */
    fun wrap(base: Context): Context {
        val tag = Prefs(base).language().tag ?: return base
        val locale = Locale.forLanguageTag(tag)
        val configuration = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(configuration)
    }

    fun language(context: Context): String =
        context.resources.configuration.locales[0].language
}
