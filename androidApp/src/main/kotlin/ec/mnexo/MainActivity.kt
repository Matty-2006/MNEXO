package ec.mnexo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private val mnexo get() = application as MnexoApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(settingsRepository = mnexo.settingsRepository, speaker = mnexo.speaker, listener = mnexo.listener)
        }
    }

    override fun onStop() {
        super.onStop()
        // Si la persona sale de la app, el asistente deja de hablar.
        mnexo.speaker.stop()
    }
}
