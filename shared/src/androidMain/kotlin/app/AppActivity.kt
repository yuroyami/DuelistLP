package app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.audio.AudioEngineContext
import app.persistence.DuelStoreFactory

/** Single-activity host. All UI, state, and navigation live inside [App]. */
class AppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DuelStoreFactory.init(applicationContext)
        AudioEngineContext.init(applicationContext)
        setContent { App() }
    }
}
