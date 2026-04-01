package ee.nekoko.nbridge

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.P)
class NBridgeProvider : ContentProvider() {
    private lateinit var backend: NBridgeBackend

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false
        NBridgeKeepAliveService.start(appContext)
        backend = NBridgeBackend.getInstance(appContext)
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        context?.applicationContext?.let { NBridgeKeepAliveService.start(it) }
        return try {
            when (method) {
                NBridgeContract.METHOD_LIST_SLOTS -> listSlots()
                NBridgeContract.METHOD_LIST_ACTIVE_CONNECTIONS -> listActiveConnections()
                NBridgeContract.METHOD_CONNECT_LOGICAL_CHANNEL -> connectLogicalChannel(arg, extras)
                NBridgeContract.METHOD_TRANSMIT_LOGICAL -> transmitLogical(arg, extras)
                NBridgeContract.METHOD_TRANSMIT_BASIC -> transmitBasic(arg, extras)
                NBridgeContract.METHOD_CLOSE_LOGICAL_CHANNEL -> closeLogicalChannel(arg, extras)
                else -> errorBundle("NOT_IMPLEMENTED", "Unknown method $method")
            }
        } catch (t: Throwable) {
            errorBundle("ERROR", t.message ?: t.javaClass.simpleName)
        }
    }

    private fun listSlots(): Bundle {
        val slots = ArrayList(backend.listSlots().map { it.toBundle() })
        return Bundle().apply {
            putBoolean(NBridgeContract.RESULT_OK, true)
            putParcelableArrayList(NBridgeContract.RESULT_SLOTS, slots)
        }
    }

    private fun listActiveConnections(): Bundle {
        val connections = ArrayList(backend.activeConnections().map { it.toBundle() })
        return Bundle().apply {
            putBoolean(NBridgeContract.RESULT_OK, true)
            putParcelableArrayList(NBridgeContract.RESULT_ACTIVE_CONNECTIONS, connections)
        }
    }

    private fun connectLogicalChannel(arg: String?, extras: Bundle?): Bundle {
        val slotId = normalizeSlotId(extras?.getString(NBridgeContract.EXTRA_SLOT_ID))
            ?: throw IllegalArgumentException("slotId is required")
        val aids = extras?.getStringArrayList(NBridgeContract.EXTRA_AIDS)
            ?: arg
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.let { ArrayList(it) }
            ?: throw IllegalArgumentException("aids is required")
        val result = backend.connectLogicalChannel(slotId, aids)
        return Bundle().apply {
            putBoolean(NBridgeContract.RESULT_OK, true)
            putString(NBridgeContract.RESULT_CONNECTION_ID, result.connectionId)
            putString(NBridgeContract.RESULT_SELECTED_AID, result.selectedAid)
            putString(NBridgeContract.RESULT_SELECT_RESPONSE, result.selectResponse)
            putInt(NBridgeContract.RESULT_CHANNEL_NUMBER, result.channelNumber)
        }
    }

    private fun transmitLogical(arg: String?, extras: Bundle?): Bundle {
        val connectionId = decodeShellValue(
            extras?.getString(NBridgeContract.EXTRA_CONNECTION_ID) ?: arg,
        )
            ?: throw IllegalArgumentException("connectionId is required")
        val apdu = extras?.getString(NBridgeContract.EXTRA_APDU)
            ?: throw IllegalArgumentException("apdu is required")
        val responseApdu = backend.transmitLogical(connectionId, apdu)
        return Bundle().apply {
            putBoolean(NBridgeContract.RESULT_OK, true)
            putString(NBridgeContract.RESULT_RESPONSE_APDU, responseApdu)
        }
    }

    private fun transmitBasic(arg: String?, extras: Bundle?): Bundle {
        val slotId = normalizeSlotId(
            extras?.getString(NBridgeContract.EXTRA_SLOT_ID) ?: arg,
        )
            ?: throw IllegalArgumentException("slotId is required")
        val apdu = extras?.getString(NBridgeContract.EXTRA_APDU)
            ?: throw IllegalArgumentException("apdu is required")
        val basicAid = extras?.getString(NBridgeContract.EXTRA_BASIC_AID)
        val responseApdu = backend.transmitBasic(slotId, apdu, basicAid)
        return Bundle().apply {
            putBoolean(NBridgeContract.RESULT_OK, true)
            putString(NBridgeContract.RESULT_RESPONSE_APDU, responseApdu)
        }
    }

    private fun closeLogicalChannel(arg: String?, extras: Bundle?): Bundle {
        val connectionId = decodeShellValue(
            extras?.getString(NBridgeContract.EXTRA_CONNECTION_ID) ?: arg,
        )
            ?: throw IllegalArgumentException("connectionId is required")
        backend.closeLogicalChannel(connectionId)
        return Bundle().apply {
            putBoolean(NBridgeContract.RESULT_OK, true)
        }
    }

    private fun decodeShellValue(value: String?): String? = value?.let(Uri::decode)

    private fun normalizeSlotId(value: String?): String? {
        val decoded = decodeShellValue(value)?.trim() ?: return null
        return when {
            decoded.startsWith("omapi:") || decoded.startsWith("tmapi:") -> decoded
            decoded.startsWith("omapi_") -> "omapi:${decoded.removePrefix("omapi_")}"
            decoded.startsWith("tmapi_") -> "tmapi:${decoded.removePrefix("tmapi_")}"
            decoded.startsWith("omapi|") -> "omapi:${decoded.removePrefix("omapi|")}"
            decoded.startsWith("tmapi|") -> "tmapi:${decoded.removePrefix("tmapi|")}"
            decoded.startsWith("SIM") -> "omapi:$decoded"
            else -> decoded.replace("%3A", ":").replace("%3a", ":")
        }
    }

    private fun errorBundle(code: String, message: String): Bundle {
        return Bundle().apply {
            putBoolean(NBridgeContract.RESULT_OK, false)
            putString(NBridgeContract.RESULT_ERROR_CODE, code)
            putString(NBridgeContract.RESULT_ERROR_MESSAGE, message)
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
