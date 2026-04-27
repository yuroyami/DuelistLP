package app.persistence

import android.content.Context

actual object DuelStoreFactory {
    private var appContext: Context? = null

    @Volatile private var instance: DuelStore? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun create(): DuelStore {
        // Singleton: DataStore must be created at most once per file per process,
        // otherwise androidx.datastore throws IllegalStateException on second creation.
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
