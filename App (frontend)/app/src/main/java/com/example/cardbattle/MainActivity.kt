package com.example.cardbattle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.cardbattle.ui.nav.AppNavHost
import com.example.cardbattle.ui.theme.CardBattleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CardBattleTheme {
                AppNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
