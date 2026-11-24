package app.spliit

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpliitApplication : Application() {

    lateinit var container: AppContainer
        private set

    /** Outlives every screen: the one-time preparation runs here, not in a composition. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scope.launch { container.prepare() }
    }
}
