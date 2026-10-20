package com.example.cardbattle.data.auth

class AuthSession {
    var idToken: String? = null
        private set

    fun save(token: String) {
        idToken = token
    }

    fun clear() {
        idToken = null
    }
}