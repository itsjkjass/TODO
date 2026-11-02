package com.pacemate.app

import android.app.Application
import com.pacemate.app.data.Repository
import com.pacemate.app.data.Settings
import com.pacemate.app.timer.Notifications

class PaceMateApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = Repository(this)
        settings = Settings(this)
        Notifications.createChannels(this)
    }

    companion object {
        lateinit var instance: PaceMateApp
            private set
    }
}
