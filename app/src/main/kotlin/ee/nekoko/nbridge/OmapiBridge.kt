package ee.nekoko.nbridge

import android.content.Context
import android.os.Build
import android.se.omapi.Channel
import android.se.omapi.Reader
import android.se.omapi.SEService
import android.se.omapi.Session
import androidx.annotation.RequiresApi
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.P)
class OmapiBridge(private val context: Context) {
    private val serviceTimeoutSeconds = 10L
    private var seService: SEService? = null
    private val connections = ConcurrentHashMap<String, OmapiConnection>()

    fun initialize() {
        ensureService()
    }

    fun closeAll() {
        connections.values.forEach { connection ->
            runCatching { connection.channel.close() }
            runCatching { connection.session.close() }
        }
        connections.clear()
    }

    fun listSlots(): List<SlotDescriptor> {
        val service = ensureService()
        return service.readers
            .filter { it.name.startsWith("SIM") }
            .map { reader ->
                val present = runCatching { reader.isSecureElementPresent }.getOrDefault(false)
                val suffix = reader.name.removePrefix("SIM")
                SlotDescriptor(
                    id = "omapi:${reader.name}",
                    transport = "omapi",
                    displayName = "O-SIM$suffix",
                    present = present,
                    readerName = reader.name,
                )
            }
    }

    fun openLogicalChannel(slotId: String, aids: List<String>): LogicalOpenResult {
        require(aids.isNotEmpty()) { "aids must not be empty" }
        aids.firstNotNullOfOrNull { aid -> findReusableConnection(slotId, aid) }?.let { reusable ->
            return LogicalOpenResult(
                connectionId = reusable.connectionId,
                selectedAid = reusable.aid,
                selectResponse = reusable.selectResponse,
                channelNumber = reusable.displayChannelNumber,
            )
        }
        val readerName = slotId.removePrefix("omapi:")
        val reader = findReader(readerName)
        val session = reader.openSession()
        var lastError: Throwable? = null

        for (aid in aids) {
            try {
                val channel = session.openLogicalChannel(hexToBytes(aid))
                if (channel != null) {
                    val connectionId = "omapi:${UUID.randomUUID()}"
                    val displayChannelNumber = resolveChannelNumber(channel).takeIf { it > 0 }
                        ?: allocateDisplayChannelNumber()
                    connections[connectionId] = OmapiConnection(
                        connectionId = connectionId,
                        slotId = slotId,
                        aid = aid,
                        displayChannelNumber = displayChannelNumber,
                        selectResponse = channel.selectResponse?.toHex(),
                        session = session,
                        channel = channel,
                    )
                    return LogicalOpenResult(
                        connectionId = connectionId,
                        selectedAid = aid,
                        selectResponse = channel.selectResponse?.toHex(),
                        channelNumber = displayChannelNumber,
                    )
                }
            } catch (t: Throwable) {
                lastError = t
            }
        }

        runCatching { session.close() }
        throw IllegalStateException(lastError?.message ?: "Failed to open any logical channel")
    }

    fun transmitLogical(connectionId: String, apduHex: String): String {
        val connection = connections[connectionId]
            ?: throw IllegalArgumentException("Unknown OMAPI connectionId")
        return connection.channel.transmit(hexToBytes(apduHex)).toHex()
    }

    fun transmitBasic(slotId: String, apduHex: String, basicAid: String?): String {
        val reader = findReader(slotId.removePrefix("omapi:"))
        val session = reader.openSession()
        val channel = if (basicAid.isNullOrBlank()) {
            session.openBasicChannel(null as ByteArray?)
        } else {
            session.openBasicChannel(hexToBytes(basicAid))
        } ?: throw IllegalStateException("Failed to open OMAPI basic channel")

        return try {
            channel.transmit(hexToBytes(apduHex)).toHex()
        } finally {
            runCatching { channel.close() }
            runCatching { session.close() }
        }
    }

    fun closeLogicalChannel(connectionId: String) {
        val connection = connections.remove(connectionId) ?: return
        runCatching { connection.channel.close() }
        runCatching { connection.session.close() }
    }

    fun activeConnections(): List<ActiveConnection> {
        return connections.map { (connectionId, connection) ->
            ActiveConnection(
                connectionId = connectionId,
                slotId = connection.slotId,
                transport = "omapi",
                channelNumber = connection.displayChannelNumber,
                aid = connection.aid,
            )
        }.sortedBy { it.slotId }
    }

    private fun findReader(readerName: String): Reader {
        val service = ensureService()
        return service.readers.firstOrNull { it.name == readerName }
            ?: throw IllegalArgumentException("Reader $readerName not found")
    }

    private fun ensureService(): SEService {
        val existing = seService
        if (existing != null && existing.isConnected) {
            return existing
        }

        val latch = CountDownLatch(1)
        val service = SEService(context, { runnable -> runnable.run() }) {
            latch.countDown()
        }
        if (!latch.await(serviceTimeoutSeconds, TimeUnit.SECONDS) || !service.isConnected) {
            throw IllegalStateException("SEService connection timeout")
        }
        seService = service
        return service
    }

    private fun resolveChannelNumber(channel: Channel): Int {
        return runCatching {
            Channel::class.java.getMethod("getChannelNumber").invoke(channel) as? Int ?: -1
        }.getOrDefault(-1)
    }

    private fun allocateDisplayChannelNumber(): Int {
        for (candidate in 1..19) {
            if (connections.values.none { it.displayChannelNumber == candidate }) {
                return candidate
            }
        }
        throw IllegalStateException("All OMAPI display channel slots are in use")
    }

    private fun findReusableConnection(slotId: String, aid: String): OmapiConnection? {
        return connections.values.firstOrNull { connection ->
            connection.slotId == slotId &&
                connection.aid.equals(aid, ignoreCase = true)
        }
    }

    private data class OmapiConnection(
        val connectionId: String,
        val slotId: String,
        val aid: String,
        val displayChannelNumber: Int,
        val selectResponse: String?,
        val session: Session,
        val channel: Channel,
    )
}
