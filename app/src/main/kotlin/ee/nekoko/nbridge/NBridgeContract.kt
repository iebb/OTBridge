package ee.nekoko.nbridge

object NBridgeContract {
    const val AUTHORITY = "ee.nekoko.nbridge.provider"

    const val METHOD_LIST_SLOTS = "listSlots"
    const val METHOD_LIST_ACTIVE_CONNECTIONS = "listActiveConnections"
    const val METHOD_CONNECT_LOGICAL_CHANNEL = "connectLogicalChannel"
    const val METHOD_TRANSMIT_LOGICAL = "transmitLogical"
    const val METHOD_TRANSMIT_BASIC = "transmitBasic"
    const val METHOD_CLOSE_LOGICAL_CHANNEL = "closeLogicalChannel"

    const val EXTRA_SLOT_ID = "slotId"
    const val EXTRA_AIDS = "aids"
    const val EXTRA_APDU = "apdu"
    const val EXTRA_CONNECTION_ID = "connectionId"
    const val EXTRA_BASIC_AID = "basicAid"

    const val RESULT_OK = "ok"
    const val RESULT_ERROR_CODE = "errorCode"
    const val RESULT_ERROR_MESSAGE = "errorMessage"
    const val RESULT_SLOTS = "slots"
    const val RESULT_ACTIVE_CONNECTIONS = "activeConnections"
    const val RESULT_CONNECTION_ID = "connectionId"
    const val RESULT_SELECTED_AID = "selectedAid"
    const val RESULT_SELECT_RESPONSE = "selectResponse"
    const val RESULT_CHANNEL_NUMBER = "channelNumber"
    const val RESULT_RESPONSE_APDU = "responseApdu"
}
