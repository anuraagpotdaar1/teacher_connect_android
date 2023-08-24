package com.anuraagpotdaar.teacherconnect

import android.content.Context

object SharedPreferencesUtil {
    private const val TEMP_PREFS = "temp_prefs"
    private const val MATCHED_ID_KEY = "user"
    const val IS_DATA_STORED_KEY = "is_data_stored"

    fun saveIdToSharedPreferences(context: Context, id: String) {
        val sharedPreferences = context.getSharedPreferences(TEMP_PREFS, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(MATCHED_ID_KEY, id)
        editor.apply()
    }

    fun getSavedIdFromSharedPreferences(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences(TEMP_PREFS, Context.MODE_PRIVATE)
        return sharedPreferences.getString(MATCHED_ID_KEY, null)
    }

    fun saveDataStoredStatus(context: Context, isDataStored: Boolean) {
        val sharedPreferences = context.getSharedPreferences(TEMP_PREFS, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean(IS_DATA_STORED_KEY, isDataStored)
        editor.apply()
    }

    fun getDataStoredStatus(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences(TEMP_PREFS, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(IS_DATA_STORED_KEY, false)
    }

    fun clearAllSharedPreferences(context: Context) {
        val sharedPreferences = context.getSharedPreferences(TEMP_PREFS, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()
    }
}
