package com.example.cardbattle.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import android.util.Log

suspend fun verifyWithBackend(idToken: String): Boolean =
    withContext(Dispatchers.IO) {
        val connection = URL("http://10.0.2.2:8080/api/v1/auth/google")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty(
                "Authorization",
                "Bearer $idToken"
            )
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val status = connection.responseCode
            Log.d("CardBattleApi", "Sign-in HTTP status: $status")
            status == 200 || status == 201
        } finally {
            connection.disconnect()
        }
    }