package com.example.cardbattle.data

import android.content.Context

class LocalAuthManager(context: Context) {
    private val prefs = context.getSharedPreferences("user_creds", Context.MODE_PRIVATE)

    fun signUp(username: String, password: String): Result<Unit> {
        if (prefs.contains(username)) {
            return Result.failure(Exception("Username already taken"))
        }
        prefs.edit().putString(username, password).apply()
        setActiveUser(username)
        return Result.success(Unit)
    }

    fun login(username: String, password: String): Result<Unit> {
        val stored = prefs.getString(username, null)
        return when {
            stored == null -> Result.failure(Exception("User not found"))
            stored != password -> Result.failure(Exception("Incorrect password"))
            else -> {
                setActiveUser(username)
                Result.success(Unit)
            }
        }
    }

    fun setActiveUser(username: String) {
        prefs.edit().putString("active_user", username).apply()
    }

    fun getActiveUser(): String {
        return prefs.getString("active_user", null) ?: "Player"
    }

    fun clearActiveUser() {
        prefs.edit().remove("active_user").apply()
    }
}