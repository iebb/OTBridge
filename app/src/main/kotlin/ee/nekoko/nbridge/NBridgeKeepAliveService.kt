package ee.nekoko.nbridge

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.P)
class NBridgeKeepAliveService : Service() {
    override fun onCreate() {
        super.onCreate()
        NBridgeBackend.getInstance(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NBridgeBackend.getInstance(applicationContext)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            runCatching {
                context.startService(Intent(context, NBridgeKeepAliveService::class.java))
            }
        }
    }
}
