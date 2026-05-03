package app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.audio.AudioEngineContext
import app.persistence.DuelStoreFactory

/**
 * Single-Activity Android host. All UI, state, and navigation live inside [App].
 *
 * MUST initialize the platform singletons ([DuelStoreFactory], [AudioEngineContext])
 * with `applicationContext` BEFORE calling [setContent] — both fail at runtime
 * with a clear error if not initialized first.
 */
class AppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DuelStoreFactory.init(applicationContext)
        AudioEngineContext.init(applicationContext)
        setContent { App() }
    }
}
