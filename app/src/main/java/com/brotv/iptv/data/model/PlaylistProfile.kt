package com.brotv.iptv.data.model

/**
 * One IPTV subscription profile as entered on the login screen:
 * list name -> username -> password -> host/URL, in that exact order,
 * matching the D-pad OK-to-advance focus order on LoginScreen.
 */
data class PlaylistProfile(
    val listName: String,
    val username: String,
    val password: String,
    val hostUrl: String,
) {
    fun isComplete(): Boolean {
        val isM3u = hostUrl.contains(".m3u", ignoreCase = true) || hostUrl.contains("type=m3u", ignoreCase = true) || (username.isBlank() && password.isBlank() && hostUrl.startsWith("http", true))
        return listName.isNotBlank() && hostUrl.isNotBlank() &&
            (isM3u || (username.isNotBlank() && password.isNotBlank()))
    }
}
