package ee.nekoko.nbridge

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.os.Build
import android.telephony.IccOpenLogicalChannelResponse
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TmapiBridge(private val context: Context) {
    private val telephonyManager: TelephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val subscriptionManager: SubscriptionManager =
        context.getSystemService(SubscriptionManager::class.java)
    private val connections = ConcurrentHashMap<String, TmapiConnection>()

    private val getUiccCardsInfo: Method? by lazy {
        runCatching { TelephonyManager::class.java.getMethod("getUiccCardsInfo") }.getOrNull()
    }
    private val getUiccSlotsInfo: Method? by lazy {
        runCatching { TelephonyManager::class.java.getMethod("getUiccSlotsInfo") }.getOrNull()
    }
    private val iccOpenLogicalChannelBySlot: Method? by lazy {
        runCatching {
            TelephonyManager::class.java.getMethod(
                "iccOpenLogicalChannelBySlot",
                Int::class.java,
                String::class.java,
                Int::class.java,
            )
        }.getOrNull()
    }
    private val iccOpenLogicalChannelByPort: Method? by lazy {
        val clazz = TelephonyManager::class.java
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clazz.getMethod(
                    "iccOpenLogicalChannelByPort",
                    Int::class.java,
                    Int::class.java,
                    String::class.java,
                    Int::class.java,
                )
            } else {
                clazz.getMethod(
                    "iccOpenLogicalChannelByPort",
                    Int::class.java,
                    String::class.java,
                    Int::class.java,
                )
            }
        }.getOrNull()
    }
    private val iccCloseLogicalChannelBySlot: Method? by lazy {
        runCatching {
            TelephonyManager::class.java.getMethod(
                "iccCloseLogicalChannelBySlot",
                Int::class.java,
                Int::class.java,
            )
        }.getOrNull()
    }
    private val iccCloseLogicalChannelByPort: Method? by lazy {
        val clazz = TelephonyManager::class.java
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clazz.getMethod(
                    "iccCloseLogicalChannelByPort",
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                )
            } else {
                clazz.getMethod(
                    "iccCloseLogicalChannelByPort",
                    Int::class.java,
                    Int::class.java,
                )
            }
        }.getOrNull()
    }
    private val iccTransmitApduLogicalChannelBySlot: Method? by lazy {
        runCatching {
            TelephonyManager::class.java.getMethod(
                "iccTransmitApduLogicalChannelBySlot",
                Int::class.java,
                Int::class.java,
                Int::class.java,
                Int::class.java,
                Int::class.java,
                Int::class.java,
                Int::class.java,
                String::class.java,
            )
        }.getOrNull()
    }
    private val iccTransmitApduLogicalChannelByPort: Method? by lazy {
        val clazz = TelephonyManager::class.java
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clazz.getMethod(
                    "iccTransmitApduLogicalChannelByPort",
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    String::class.java,
                )
            } else {
                clazz.getMethod(
                    "iccTransmitApduLogicalChannelByPort",
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    String::class.java,
                )
            }
        }.getOrNull()
    }
    private val iccTransmitApduBasicChannelBySlot: Method? by lazy {
        runCatching {
            TelephonyManager::class.java.getMethod(
                "iccTransmitApduBasicChannelBySlot",
                Int::class.java,
                Int::class.java,
                Int::class.java,
                Int::class.java,
                Int::class.java,
                Int::class.java,
                String::class.java,
            )
        }.getOrNull()
    }
    private val iccTransmitApduBasicChannelByPort: Method? by lazy {
        val clazz = TelephonyManager::class.java
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clazz.getMethod(
                    "iccTransmitApduBasicChannelByPort",
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    String::class.java,
                )
            } else {
                clazz.getMethod(
                    "iccTransmitApduBasicChannelByPort",
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    String::class.java,
                )
            }
        }.getOrNull()
    }

    fun isAvailable(): Boolean {
        val hasModifyPhoneState =
            context.checkSelfPermission(Manifest.permission.MODIFY_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED
        return hasModifyPhoneState &&
            (iccOpenLogicalChannelByPort != null || iccOpenLogicalChannelBySlot != null)
    }

    fun closeAll() {
        connections.values.forEach {
            runCatching { closeLogicalChannel(it.slotIndex, it.portIndex, it.channelNumber) }
        }
        connections.clear()
    }

    fun listSlots(): List<SlotDescriptor> {
        if (!isAvailable()) {
            return emptyList()
        }

        val slots = mutableListOf<SlotDescriptor>()
        val activeSubscriptionSlots = activeSubscriptionSlotIndexes()
        val slotCardStates = readUiccSlotCardStates()
        val cardsInfo = runCatching { getUiccCardsInfo?.invoke(telephonyManager) as? List<*> }.getOrNull()

        cardsInfo?.forEach { card ->
            if (card == null) return@forEach
            val cardClass = card.javaClass
            val slotIndex = readSlotIndex(cardClass, card)
            if (slotIndex < 0) return@forEach

            val isEuicc = runCatching {
                cardClass.getMethod("isEuicc").invoke(card) as? Boolean ?: false
            }.getOrDefault(false)
            val ports = readPorts(card, cardClass, slotIndex)
            val usePortSuffix = shouldUsePortSuffix(ports)
            val simState = runCatching { telephonyManager.getSimState(slotIndex) }.getOrDefault(0)

            ports.forEach { port ->
                val portIndex = port["portIndex"] as Int
                val logicalSlotIndex = port["logicalSlotIndex"] as Int
                val isActive = (port["isActive"] as? Int) == 1
                val present = resolvePresent(
                    slotIndex = slotIndex,
                    portIndex = portIndex,
                    isActivePort = isActive,
                    simState = simState,
                    activeSubscriptionSlots = activeSubscriptionSlots,
                    slotCardStates = slotCardStates,
                )
                Log.i(
                    TAG,
                    "TMAPI slot=$slotIndex port=$portIndex present=$present " +
                        "(slotCardState=${slotCardStates[slotIndex]}, simState=$simState, " +
                        "isActivePort=$isActive, activeSub=${activeSubscriptionSlots.contains(slotIndex)})",
                )
                slots += SlotDescriptor(
                    id = "tmapi:slot:$slotIndex:port:$portIndex",
                    transport = "tmapi",
                    displayName = buildDisplayName(slotIndex, portIndex, usePortSuffix),
                    present = present,
                    slotIndex = slotIndex,
                    portIndex = portIndex,
                    logicalSlotIndex = logicalSlotIndex,
                    simState = simState,
                    isEuicc = isEuicc,
                )
            }
        }

        if (slots.isNotEmpty()) {
            return slots
        }

        val count = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            telephonyManager.activeModemCount
        } else {
            1
        }
        for (slotIndex in 0 until count) {
            val simState = runCatching { telephonyManager.getSimState(slotIndex) }.getOrDefault(0)
            val present = resolvePresent(
                slotIndex = slotIndex,
                portIndex = 0,
                isActivePort = false,
                simState = simState,
                activeSubscriptionSlots = activeSubscriptionSlots,
                slotCardStates = slotCardStates,
            )
            Log.i(
                TAG,
                "TMAPI fallback slot=$slotIndex port=0 present=$present " +
                    "(slotCardState=${slotCardStates[slotIndex]}, simState=$simState, " +
                    "activeSub=${activeSubscriptionSlots.contains(slotIndex)})",
            )
            slots += SlotDescriptor(
                id = "tmapi:slot:$slotIndex:port:0",
                transport = "tmapi",
                displayName = buildDisplayName(slotIndex, 0, false),
                present = present,
                slotIndex = slotIndex,
                portIndex = 0,
                logicalSlotIndex = slotIndex,
                simState = simState,
                isEuicc = true,
            )
        }
        return slots
    }

    private fun shouldUsePortSuffix(ports: List<Map<String, Int>>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        if (ports.size <= 1 && ports.firstOrNull()?.get("portIndex") == 0) {
            return false
        }
        return ports.any { (it["portIndex"] ?: 0) > 0 } || ports.size > 1
    }

    private fun buildDisplayName(slotIndex: Int, portIndex: Int, usePortSuffix: Boolean): String {
        return if (usePortSuffix) {
            "T-SIM${slotIndex + 1}p$portIndex"
        } else {
            "T-SIM${slotIndex + 1}"
        }
    }

    private fun resolvePresent(
        slotIndex: Int,
        portIndex: Int,
        isActivePort: Boolean,
        simState: Int,
        activeSubscriptionSlots: Set<Int>,
        slotCardStates: Map<Int, Int>,
    ): Boolean {
        when (slotCardStates[slotIndex]) {
            CARD_STATE_INFO_ABSENT -> return false
            CARD_STATE_INFO_PRESENT,
            CARD_STATE_INFO_RESTRICTED,
            CARD_STATE_INFO_ERROR -> return true
        }
        if (activeSubscriptionSlots.contains(slotIndex)) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && portIndex > 0) {
            return isActivePort
        }
        return simState != TelephonyManager.SIM_STATE_ABSENT &&
            simState != TelephonyManager.SIM_STATE_UNKNOWN
    }

    private fun activeSubscriptionSlotIndexes(): Set<Int> {
        return runCatching {
            subscriptionManager.activeSubscriptionInfoList.orEmpty()
                .mapNotNull { info -> info.simSlotIndex.takeIf { it >= 0 } }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private fun readUiccSlotCardStates(): Map<Int, Int> {
        val slotInfos = runCatching {
            getUiccSlotsInfo?.invoke(telephonyManager) as? Array<*>
        }.getOrNull() ?: return emptyMap()
        val result = mutableMapOf<Int, Int>()
        slotInfos.forEachIndexed { slotIndex, slotInfo ->
            if (slotInfo == null) return@forEachIndexed
            val cardState = runCatching {
                slotInfo.javaClass.getMethod("getCardStateInfo").invoke(slotInfo) as? Int
            }.getOrNull()
            if (cardState != null) {
                result[slotIndex] = cardState
            }
        }
        return result
    }

    fun openLogicalChannel(slotId: String, aids: List<String>): LogicalOpenResult {
        ensureAvailable()
        require(aids.isNotEmpty()) { "aids must not be empty" }
        val slot = parseSlot(slotId)
        sendTerminalCapabilities(slot.slotIndex, slot.portIndex)

        aids.firstNotNullOfOrNull { aid -> findReusableConnection(slot.slotIndex, slot.portIndex, aid) }?.let { reusable ->
            return LogicalOpenResult(
                connectionId = reusable.connectionId,
                selectedAid = reusable.aid,
                selectResponse = reusable.selectResponse,
                channelNumber = reusable.channelNumber,
            )
        }

        var lastError: Throwable? = null
        for (aid in aids) {
            try {
                val response = openLogicalChannel(slot.slotIndex, slot.portIndex, aid)
                if (response.status == IccOpenLogicalChannelResponse.STATUS_NO_ERROR) {
                    val connectionId = "tmapi:${UUID.randomUUID()}"
                    connections[connectionId] = TmapiConnection(
                        connectionId = connectionId,
                        slotIndex = slot.slotIndex,
                        portIndex = slot.portIndex,
                        channelNumber = response.channel,
                        aid = aid,
                        selectResponse = response.selectResponse?.toHex(),
                    )
                    return LogicalOpenResult(
                        connectionId = connectionId,
                        selectedAid = aid,
                        selectResponse = response.selectResponse?.toHex(),
                        channelNumber = response.channel,
                    )
                }
                lastError = IllegalStateException("Open failed with status ${response.status}")
            } catch (t: Throwable) {
                lastError = t
            }
        }

        throw IllegalStateException(lastError?.message ?: "Failed to open any TMAPI logical channel")
    }

    fun transmitLogical(connectionId: String, apduHex: String): String {
        val connection = connections[connectionId]
            ?: throw IllegalArgumentException("Unknown TMAPI connectionId")
        val apdu = parseApdu(apduHex)
        return transmitLogicalChannel(
            slotIndex = connection.slotIndex,
            portIndex = connection.portIndex,
            channel = connection.channelNumber,
            apdu = apdu,
        ) ?: throw IllegalStateException("TMAPI logical transmit returned null")
    }

    fun transmitBasic(slotId: String, apduHex: String): String {
        ensureAvailable()
        val slot = parseSlot(slotId)
        val apdu = parseApdu(apduHex)
        return transmitBasicChannel(
            slotIndex = slot.slotIndex,
            portIndex = slot.portIndex,
            apdu = apdu,
        ) ?: throw IllegalStateException("TMAPI basic transmit returned null")
    }

    fun closeConnection(connectionId: String) {
        val connection = connections.remove(connectionId) ?: return
        closeLogicalChannel(connection.slotIndex, connection.portIndex, connection.channelNumber)
    }

    fun activeConnections(): List<ActiveConnection> {
        return connections.map { (connectionId, connection) ->
            ActiveConnection(
                connectionId = connectionId,
                slotId = "tmapi:slot:${connection.slotIndex}:port:${connection.portIndex}",
                transport = "tmapi",
                channelNumber = connection.channelNumber,
                aid = connection.aid,
            )
        }.sortedBy { it.slotId }
    }

    private fun findReusableConnection(slotIndex: Int, portIndex: Int, aid: String): TmapiConnection? {
        return connections.values.firstOrNull { connection ->
            connection.slotIndex == slotIndex &&
                connection.portIndex == portIndex &&
                connection.aid.equals(aid, ignoreCase = true)
        }
    }

    private fun ensureAvailable() {
        check(isAvailable()) {
            "TMAPI requires a privileged install with MODIFY_PHONE_STATE granted"
        }
    }

    private fun parseSlot(slotId: String): ParsedSlot {
        val parts = slotId.split(':')
        require(parts.size >= 5 && parts[0] == "tmapi") { "Invalid TMAPI slotId" }
        return ParsedSlot(
            slotIndex = parts[2].toInt(),
            portIndex = parts[4].toInt(),
        )
    }

    private fun readSlotIndex(cardClass: Class<*>, card: Any): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { cardClass.getMethod("getPhysicalSlotIndex").invoke(card) as? Int ?: -1 }
                .getOrElse {
                    runCatching { cardClass.getMethod("getSlotIndex").invoke(card) as? Int ?: -1 }
                        .getOrDefault(-1)
                }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { cardClass.getMethod("getSlotIndex").invoke(card) as? Int ?: -1 }
                .getOrDefault(-1)
        } else {
            -1
        }
    }

    private fun readPorts(card: Any, cardClass: Class<*>, slotIndex: Int): List<Map<String, Int>> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return listOf(mapOf("portIndex" to 0, "logicalSlotIndex" to slotIndex, "isActive" to 0))
        }

        return runCatching {
            val ports = cardClass.getMethod("getPorts").invoke(card) as? Collection<*>
            val result = mutableListOf<Map<String, Int>>()
            ports?.forEach { port ->
                if (port == null) return@forEach
                val portClass = port.javaClass
                val portIndex = portClass.getMethod("getPortIndex").invoke(port) as? Int ?: 0
                val logicalSlotIndex =
                    portClass.getMethod("getLogicalSlotIndex").invoke(port) as? Int ?: slotIndex
                val isActive =
                    (portClass.getMethod("isActive").invoke(port) as? Boolean == true)
                result += mapOf(
                    "portIndex" to portIndex,
                    "logicalSlotIndex" to logicalSlotIndex,
                    "isActive" to if (isActive) 1 else 0,
                )
            }
            result.ifEmpty { listOf(mapOf("portIndex" to 0, "logicalSlotIndex" to slotIndex, "isActive" to 0)) }
        }.getOrDefault(listOf(mapOf("portIndex" to 0, "logicalSlotIndex" to slotIndex, "isActive" to 0)))
    }

    private fun sendTerminalCapabilities(slotIndex: Int, portIndex: Int) {
        runCatching {
            transmitBasicChannel(
                slotIndex = slotIndex,
                portIndex = portIndex,
                apdu = ParsedApdu(
                    cla = 0x80,
                    ins = 0xAA,
                    p1 = 0x00,
                    p2 = 0x00,
                    p3 = 0x0A,
                    data = "A9088100820101830107",
                ),
            )
        }
    }

    private fun openLogicalChannel(slotIndex: Int, portIndex: Int, aid: String): IccOpenLogicalChannelResponse {
        return when {
            iccOpenLogicalChannelByPort != null -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    iccOpenLogicalChannelByPort!!.invoke(
                        telephonyManager,
                        slotIndex,
                        portIndex,
                        aid,
                        0,
                    ) as IccOpenLogicalChannelResponse
                } else {
                    iccOpenLogicalChannelByPort!!.invoke(
                        telephonyManager,
                        portIndex,
                        aid,
                        0,
                    ) as IccOpenLogicalChannelResponse
                }
            }
            iccOpenLogicalChannelBySlot != null -> {
                iccOpenLogicalChannelBySlot!!.invoke(
                    telephonyManager,
                    slotIndex,
                    aid,
                    0,
                ) as IccOpenLogicalChannelResponse
            }
            else -> throw IllegalStateException("Logical channel APIs not available")
        }
    }

    private fun closeLogicalChannel(slotIndex: Int, portIndex: Int, channel: Int) {
        when {
            iccCloseLogicalChannelByPort != null -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    iccCloseLogicalChannelByPort!!.invoke(telephonyManager, slotIndex, portIndex, channel)
                } else {
                    iccCloseLogicalChannelByPort!!.invoke(telephonyManager, portIndex, channel)
                }
            }
            iccCloseLogicalChannelBySlot != null -> {
                iccCloseLogicalChannelBySlot!!.invoke(telephonyManager, slotIndex, channel)
            }
            else -> throw IllegalStateException("Close logical channel APIs not available")
        }
    }

    private fun transmitLogicalChannel(
        slotIndex: Int,
        portIndex: Int,
        channel: Int,
        apdu: ParsedApdu,
    ): String? {
        return when {
            iccTransmitApduLogicalChannelByPort != null -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    iccTransmitApduLogicalChannelByPort!!.invoke(
                        telephonyManager,
                        slotIndex,
                        portIndex,
                        channel,
                        apdu.cla,
                        apdu.ins,
                        apdu.p1,
                        apdu.p2,
                        apdu.p3,
                        apdu.data,
                    ) as String?
                } else {
                    iccTransmitApduLogicalChannelByPort!!.invoke(
                        telephonyManager,
                        portIndex,
                        channel,
                        apdu.cla,
                        apdu.ins,
                        apdu.p1,
                        apdu.p2,
                        apdu.p3,
                        apdu.data,
                    ) as String?
                }
            }
            iccTransmitApduLogicalChannelBySlot != null -> {
                iccTransmitApduLogicalChannelBySlot!!.invoke(
                    telephonyManager,
                    slotIndex,
                    channel,
                    apdu.cla,
                    apdu.ins,
                    apdu.p1,
                    apdu.p2,
                    apdu.p3,
                    apdu.data,
                ) as String?
            }
            else -> throw IllegalStateException("Logical transmit APIs not available")
        }
    }

    private fun transmitBasicChannel(slotIndex: Int, portIndex: Int, apdu: ParsedApdu): String? {
        return when {
            iccTransmitApduBasicChannelByPort != null -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    iccTransmitApduBasicChannelByPort!!.invoke(
                        telephonyManager,
                        slotIndex,
                        portIndex,
                        apdu.cla,
                        apdu.ins,
                        apdu.p1,
                        apdu.p2,
                        apdu.p3,
                        apdu.data,
                    ) as String?
                } else {
                    iccTransmitApduBasicChannelByPort!!.invoke(
                        telephonyManager,
                        portIndex,
                        apdu.cla,
                        apdu.ins,
                        apdu.p1,
                        apdu.p2,
                        apdu.p3,
                        apdu.data,
                    ) as String?
                }
            }
            iccTransmitApduBasicChannelBySlot != null -> {
                iccTransmitApduBasicChannelBySlot!!.invoke(
                    telephonyManager,
                    slotIndex,
                    apdu.cla,
                    apdu.ins,
                    apdu.p1,
                    apdu.p2,
                    apdu.p3,
                    apdu.data,
                ) as String?
            }
            else -> throw IllegalStateException("Basic transmit APIs not available")
        }
    }

    private data class ParsedSlot(
        val slotIndex: Int,
        val portIndex: Int,
    )

    private data class TmapiConnection(
        val connectionId: String,
        val slotIndex: Int,
        val portIndex: Int,
        val channelNumber: Int,
        val aid: String,
        val selectResponse: String?,
    )

    companion object {
        private const val TAG = "TmapiBridge"
        private const val CARD_STATE_INFO_ABSENT = 1
        private const val CARD_STATE_INFO_PRESENT = 2
        private const val CARD_STATE_INFO_ERROR = 3
        private const val CARD_STATE_INFO_RESTRICTED = 4
    }
}
