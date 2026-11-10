package com.jlptdojo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jlptdojo.app.data.Progress
import com.jlptdojo.app.data.Speaker
import com.jlptdojo.app.ui.DojoTheme
import com.jlptdojo.app.ui.JlptApp

class MainActivity : ComponentActivity() {
    private var speaker: Speaker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val progress = Progress(applicationContext)
        val tts = Speaker(applicationContext)
        speaker = tts
        setContent {
            DojoTheme {
                JlptApp(progress, tts)
            }
        }
    }

    override fun onDestroy() {
        speaker?.shutdown()
        speaker = null
        super.onDestroy()
    }
}
