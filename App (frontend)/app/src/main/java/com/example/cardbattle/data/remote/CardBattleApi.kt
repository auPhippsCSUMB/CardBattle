package com.example.cardbattle.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

suspend fun verifyWithBackend(idToken: String): Boolean =
    withContext(Dispatchers.IO) {
        val connection = URL("http://10.0.2.2:8080/api/v1/auth/me")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "Authorization",
                "Bearer $idToken"
            )
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.responseCode == 200
        } finally {
            connection.disconnect()
        }
    }