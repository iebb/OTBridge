package ee.nekoko.nbridge

import android.content.Context

class SlotSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("nbridge_slots", Context.MODE_PRIVATE)

    fun isEnabled(slotId: String): Boolean = prefs.getBoolean("slot_enabled_$slotId", true)

    fun setEnabled(slotId: String, enabled: Boolean) {
        prefs.edit().putBoolean("slot_enabled_$slotId", enabled).apply()
    }
}
