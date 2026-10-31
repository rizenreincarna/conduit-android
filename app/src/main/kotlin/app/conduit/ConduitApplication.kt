package app.conduit

import android.app.Application
import android.content.Context

// android: Application owns platform services; pure tests inject scratch stores instead.
lateinit var appContext: Context
    private set

class ConduitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        UserDefaults.install(getSharedPreferences("conduit.defaults", MODE_PRIVATE))
        AppLocalization.install(this)
    }
}
