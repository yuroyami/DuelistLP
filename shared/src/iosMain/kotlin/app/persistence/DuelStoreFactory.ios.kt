package app.persistence

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * iOS [DuelStoreFactory] actual. Resolves `Documents/` via NSFileManager and
 * memoizes a single [DuelStore] (DataStore throws on second-creation against
 * the same file).
 */
actual object DuelStoreFactory {
    private var instance: DuelStore? = null

    actual fun create(): DuelStore {
        instance?.let { return it }
        val docs: NSURL = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ) ?: error("Could not resolve documents directory")
        val path = (docs.path ?: error("Documents URL has no path")) + "/" + DuelStore.FILE_NAME
        return DuelStore(path).also { instance = it }
    }
}
