package ee.nekoko.nbridge

import android.os.Bundle

data class SlotDescriptor(
    val id: String,
    val transport: String,
    val displayName: String,
    val present: Boolean,
    val slotIndex: Int? = null,
    val portIndex: Int? = null,
    val logicalSlotIndex: Int? = null,
    val readerName: String? = null,
    val simState: Int? = null,
    val isEuicc: Boolean? = null,
) {
    fun toBundle(): Bundle =
        Bundle().apply {
            putString("id", id)
            putString("transport", transport)
            putString("displayName", displayName)
            putBoolean("present", present)
            slotIndex?.let { putInt("slotIndex", it) }
            portIndex?.let { putInt("portIndex", it) }
            logicalSlotIndex?.let { putInt("logicalSlotIndex", it) }
            readerName?.let { putString("readerName", it) }
            simState?.let { putInt("simState", it) }
            isEuicc?.let { putBoolean("isEuicc", it) }
        }
}

data class LogicalOpenResult(
    val connectionId: String,
    val selectedAid: String,
    val selectResponse: String?,
    val channelNumber: Int,
)

data class ActiveConnection(
    val connectionId: String,
    val slotId: String,
    val transport: String,
    val channelNumber: Int,
    val aid: String,
) {
    fun toBundle(): Bundle =
        Bundle().apply {
            putString("connectionId", connectionId)
            putString("slotId", slotId)
            putString("transport", transport)
            putInt("channelNumber", channelNumber)
            putString("aid", aid)
        }
}
