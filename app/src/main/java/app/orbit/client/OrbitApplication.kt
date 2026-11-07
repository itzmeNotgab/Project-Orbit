package app.orbit.client

import android.app.Application

class OrbitApplication : Application() {
    override fun onCreate() { super.onCreate(); RelayController.init(this); AppStore.init(this) }
}
