package com.example.linkup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.linkup.app.LinkUpApp
import com.example.linkup.ui.theme.LinkUpTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single Android entry point. Feature teams should not need to edit this file. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LinkUpTheme { LinkUpApp() } }
    }
}
