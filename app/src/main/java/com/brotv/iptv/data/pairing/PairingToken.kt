package com.brotv.iptv.data.pairing

import java.util.UUID

data class PairingToken(
    val value: String = UUID.randomUUID().toString(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    val ttlMillis: Long = TTL_MILLIS,
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis - createdAtMillis > ttlMillis
    companion object { const val TTL_MILLIS = 5 * 60 * 1000L }
}
