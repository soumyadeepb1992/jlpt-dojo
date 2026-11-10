package com.jlptdojo.app.data

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Japanese pronunciation through the phone's text-to-speech engine (Samsung or Google TTS). */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    var ready by mutableStateOf(false)
        private set

    init {
        tts = try {
            TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            null
        }
    }

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status == TextToSpeech.SUCCESS) {
            val result = engine.setLanguage(Locale.JAPANESE)
            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            engine.setSpeechRate(0.9f)
        }
    }

    fun speak(text: String) {
        if (!ready) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jlpt-dojo")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
