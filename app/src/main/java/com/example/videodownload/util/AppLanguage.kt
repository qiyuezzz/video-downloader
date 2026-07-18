package com.example.videodownload.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** 应用语言持久化与本地化 Context 创建。 */
object AppLanguage {
    const val CHINESE = "zh"
    const val ENGLISH = "en"

    private const val PREFS_NAME = "app_language"
    private const val LANGUAGE_KEY = "selected_language"

    fun selectedLanguage(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(LANGUAGE_KEY, null)
            ?.takeIf { it == CHINESE || it == ENGLISH }

    fun setLanguage(context: Context, language: String) {
        val normalized = language.takeIf { it == CHINESE || it == ENGLISH } ?: CHINESE
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LANGUAGE_KEY, normalized)
            .apply()
        applyToResources(context.applicationContext, normalized)
    }

    fun wrapContext(context: Context): Context {
        val language = selectedLanguage(context) ?: return context
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }

    @Suppress("DEPRECATION")
    private fun applyToResources(context: Context, language: String) {
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }
}
