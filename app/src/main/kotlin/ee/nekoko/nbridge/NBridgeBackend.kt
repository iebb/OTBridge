package ee.nekoko.nbridge

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.CopyOnWriteArraySet

@RequiresApi(Build.VERSION_CODES.P)
class NBridgeBackend(private val context: Context) {
    private val omapi = OmapiBridge(context)
    private val tmapi = TmapiBridge(context)
    private val slotSettings = SlotSettingsStore(context)
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val simEventGeneration = AtomicLong(0)
    @Volatile
    private var lastSimAction: String? = null

    private val simStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            lastSimAction = action
            val generation = simEventGeneration.incrementAndGet()
            omapi.closeAll()
            tmapi.closeAll()
            listeners.forEach { listener ->
                runCatching { listener.onSimStateChanged(action, generation) }
            }
        }
    }
    @Volatile
    private var initialized = false

    fun initialize() {
        if (initialized) return
        val filter = IntentFilter("android.intent.action.SIM_STATE_CHANGED").apply {
            addAction("android.telephony.action.SIM_CARD_STATE_CHANGED")
            addAction("android.telephony.action.SIM_APPLICATION_STATE_CHANGED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(simStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(simStateReceiver, filter)
        }
        initialized = true
    }

    fun listSlots(): List<SlotDescriptor> = listAllSlots().filter {
        slotSettings.isEnabled(it.id)
    }

    fun listAllSlots(): List<SlotDescriptor> {
        val omapiSlots = runCatching { omapi.listSlots() }
            .onFailure { Log.w(TAG, "OMAPI slot enumeration failed", it) }
            .getOrDefault(emptyList())
        val tmapiSlots = runCatching { tmapi.listSlots() }
            .onFailure { Log.w(TAG, "TMAPI slot enumeration failed", it) }
            .getOrDefault(emptyList())
            .map { slot -> reconcileTmapiPresence(slot, omapiSlots) }
        Log.i(
            TAG,
            "listAllSlots -> omapi=${omapiSlots.joinToString { "${it.displayName}:${it.present}" }} " +
                "tmapi=${tmapiSlots.joinToString { "${it.displayName}:${it.present}" }}",
        )
        return omapiSlots + tmapiSlots
    }

    private fun reconcileTmapiPresence(
        slot: SlotDescriptor,
        omapiSlots: List<SlotDescriptor>,
    ): SlotDescriptor {
        if (slot.transport != "tmapi") {
            return slot
        }
        val slotIndex = slot.slotIndex ?: return slot
        val matchingOmapi = omapiSlots.firstOrNull { omapiSlot ->
            omapiSlot.transport == "omapi" &&
                omapiSlot.readerName == "SIM${slotIndex + 1}"
        } ?: return slot
        return if (!matchingOmapi.present && slot.present) {
            Log.i(TAG, "Forcing ${slot.displayName} absent because ${matchingOmapi.displayName} is absent")
            slot.copy(present = false)
        } else {
            slot
        }
    }

    fun isSlotEnabled(slotId: String): Boolean = slotSettings.isEnabled(slotId)

    fun setSlotEnabled(slotId: String, enabled: Boolean) {
        slotSettings.setEnabled(slotId, enabled)
    }

    fun addListener(listener: Listener, replayLatest: Boolean = true) {
        listeners += listener
        if (replayLatest) {
            val generation = simEventGeneration.get()
            if (generation > 0L) {
                runCatching { listener.onSimStateChanged(lastSimAction, generation) }
            }
        }
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    fun activeConnections(): List<ActiveConnection> {
        return (omapi.activeConnections() + tmapi.activeConnections()).sortedBy { it.slotId }
    }

    @Synchronized
    fun connectLogicalChannel(slotId: String, aids: List<String>): LogicalOpenResult {
        return when {
            slotId.startsWith("omapi:") -> omapi.openLogicalChannel(slotId, aids)
            slotId.startsWith("tmapi:") -> tmapi.openLogicalChannel(slotId, aids)
            else -> throw IllegalArgumentException("Unknown slot transport: $slotId")
        }
    }

    @Synchronized
    fun transmitLogical(connectionId: String, apduHex: String): String {
        return when {
            connectionId.startsWith("omapi:") -> omapi.transmitLogical(connectionId, apduHex)
            connectionId.startsWith("tmapi:") -> tmapi.transmitLogical(connectionId, apduHex)
            else -> throw IllegalArgumentException("Unknown connection transport")
        }
    }

    @Synchronized
    fun transmitBasic(slotId: String, apduHex: String, basicAid: String?): String {
        return when {
            slotId.startsWith("omapi:") -> omapi.transmitBasic(slotId, apduHex, basicAid)
            slotId.startsWith("tmapi:") -> tmapi.transmitBasic(slotId, apduHex)
            else -> throw IllegalArgumentException("Unknown slot transport: $slotId")
        }
    }

    @Synchronized
    fun closeLogicalChannel(connectionId: String) {
        when {
            connectionId.startsWith("omapi:") -> omapi.closeLogicalChannel(connectionId)
            connectionId.startsWith("tmapi:") -> tmapi.closeConnection(connectionId)
            else -> throw IllegalArgumentException("Unknown connection transport")
        }
    }

    companion object {
        private const val TAG = "NBridgeBackend"

        @Volatile
        private var instance: NBridgeBackend? = null

        fun getInstance(context: Context): NBridgeBackend {
            return instance ?: synchronized(this) {
                instance ?: NBridgeBackend(context.applicationContext).also { backend ->
                    backend.initialize()
                    instance = backend
                }
            }
        }
    }

    interface Listener {
        fun onSimStateChanged(action: String?, generation: Long)
    }
}
