package com.badens.guitartuner

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.badens.guitartuner.ui.MicPermissionGate
import com.badens.guitartuner.ui.TunerRoute
import com.badens.guitartuner.ui.theme.GuitarTunerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            GuitarTunerTheme {
                MicPermissionGate {
                    TunerRoute()
                }
            }
        }
    }
}
