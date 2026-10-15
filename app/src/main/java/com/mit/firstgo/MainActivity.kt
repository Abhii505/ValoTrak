package com.mit.firstgo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mit.firstgo.ui.ValTrackerScreen
import com.mit.firstgo.ui.theme.FirstGOTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FirstGOTheme {
                ValTrackerScreen()
            }
        }
    }
}