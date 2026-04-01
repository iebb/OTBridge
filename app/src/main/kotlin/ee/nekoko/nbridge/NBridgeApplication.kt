package ee.nekoko.nbridge

import android.app.Application
import com.google.android.material.color.DynamicColors

class NBridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        NBridgeBackend.getInstance(applicationContext)
        NBridgeKeepAliveService.start(this)
    }
}
