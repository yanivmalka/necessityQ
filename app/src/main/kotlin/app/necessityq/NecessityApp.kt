package app.necessityq

import android.app.Application

class NecessityApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        app.necessityq.platform.CrashReport.install(this)
        container = AppContainer(this)
    }
}
