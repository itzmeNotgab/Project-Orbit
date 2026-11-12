package app.orbit.client

import android.app.Application

class OrbitApplication : Application() {
    override fun onCreate() { super.onCreate(); OrbitSettings.init(this); AppStore.init(this); AccountState.init(this) }
}
