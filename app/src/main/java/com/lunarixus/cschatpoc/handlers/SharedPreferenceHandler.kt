package com.lunarixus.cschatpoc.handlers

import android.content.Context
import android.content.SharedPreferences

object SharedPreferenceHandler {

    private const val PREF_NAME = "CSChatPreferences"
    private lateinit var preferences: SharedPreferences

    fun init(context: Context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // Store a string in a shared preference
    fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    // Get a string from a shared preference
    fun getString(key: String, defaultValue: String = ""): String {
        return preferences.getString(key, defaultValue) ?: defaultValue
    }
}
