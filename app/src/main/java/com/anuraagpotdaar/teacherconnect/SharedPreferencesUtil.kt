package com.anuraagpotdaar.teacherconnect

import android.content.Context

object SharedPreferencesUtil {
    private const val TEMP_PREFS = "temp_prefs"
    private const val MATCHED_ID_KEY = "user"

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
}
