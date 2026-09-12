package com.brotv.iptv.data.pairing

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.brotv.iptv.data.model.PlaylistProfile
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.NetworkInterface
import java.util.Collections

class QrPairingManager(private val port: Int = 8988) {
    private var server: PairingServer? = null

    @Synchronized
    fun startSession(mode: PairingMode, onCredentialsReceived: (PlaylistProfile) -> Unit): PairingSession? {
        stopSession()
        val localIp = findLocalIpv4Address() ?: return null
        val token = PairingToken()
        val pairingUrl = "http://$localIp:$port/pair?token=${token.value}"
        val newServer = PairingServer(port, token, mode, onCredentialsReceived)
        try {
            newServer.start(SOCKET_READ_TIMEOUT_MILLIS, false)
            server = newServer
        } catch (error: Throwable) {
            runCatching { newServer.stop() }
            server = null
            throw error
        }
        return PairingSession(token, pairingUrl, generateQrBitmap(pairingUrl), mode)
    }

    @Synchronized
    fun stopSession() {
        server?.stop()
        server = null
    }

    private fun findLocalIpv4Address(): String? {
        val addresses = Collections.list(NetworkInterface.getNetworkInterfaces())
            .flatMap { Collections.list(it.inetAddresses) }
            .filter { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
        // Prefer a private/LAN address over VPN or other routable interfaces.
        return (addresses.firstOrNull { it.isSiteLocalAddress } ?: addresses.firstOrNull())?.hostAddress
    }

    private fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).also { bitmap ->
            for (x in 0 until size) for (y in 0 until size) bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }

    data class PairingSession(val token: PairingToken, val pairingUrl: String, val qrBitmap: Bitmap, val mode: PairingMode)
    private companion object { const val SOCKET_READ_TIMEOUT_MILLIS = 5000 }
}
