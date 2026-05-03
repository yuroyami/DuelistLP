package app.persistence

import android.content.Context

/**
 * Android [DuelStoreFactory] actual. [init] must be called from
 * `AppActivity.onCreate` before [create] — DataStore needs the app Context to
 * resolve `filesDir`.
 *
 * Singleton: DataStore throws `IllegalStateException` on second creation
 * against the same file in one process.
 */
actual object DuelStoreFactory {
    private var appContext: Context? = null

    @Volatile private var instance: DuelStore? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun create(): DuelStore {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: run {
                val ctx = appContext
                    ?: error("DuelStoreFactory.init(context) must be called before create()")
                val file = ctx.filesDir.resolve(DuelStore.FILE_NAME)
                DuelStore(file.absolutePath).also { instance = it }
            }
        }
    }
}
