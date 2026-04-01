package ee.nekoko.nbridge

internal fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }

internal fun hexToBytes(hex: String): ByteArray {
    val clean = hex.replace(" ", "").replace("\n", "").replace("\t", "")
    require(clean.length % 2 == 0) { "Hex string must have even length" }
    return ByteArray(clean.length / 2) { index ->
        clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

internal data class ParsedApdu(
    val cla: Int,
    val ins: Int,
    val p1: Int,
    val p2: Int,
    val p3: Int,
    val data: String?,
)

internal fun parseApdu(apduHex: String): ParsedApdu {
    val tx = hexToBytes(apduHex)
    require(tx.size >= 4) { "APDU too short" }

    val cla = tx[0].toUByte().toInt()
    val ins = tx[1].toUByte().toInt()
    val p1 = tx[2].toUByte().toInt()
    val p2 = tx[3].toUByte().toInt()
    var p3 = 0
    var data: String? = null

    if (tx.size > 4) {
        p3 = tx[4].toUByte().toInt()
        if (tx.size > 5) {
            data = tx.sliceArray(5 until tx.size).toHex()
        }
    }

    return ParsedApdu(cla = cla, ins = ins, p1 = p1, p2 = p2, p3 = p3, data = data)
}
