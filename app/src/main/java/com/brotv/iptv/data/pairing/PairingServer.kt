package com.brotv.iptv.data.pairing

import com.brotv.iptv.data.model.PlaylistProfile
import fi.iki.elonen.NanoHTTPD
import java.net.URLDecoder

class PairingServer(
    port: Int,
    private val expectedToken: PairingToken,
    private val mode: PairingMode,
    private val onCredentialsReceived: (PlaylistProfile) -> Unit,
) : NanoHTTPD(port) {
    @Volatile private var tokenConsumed = false

    override fun serve(session: IHTTPSession): Response = when {
        session.method == Method.GET && session.uri == "/pair" -> serveForm(session.parms["token"].orEmpty())
        session.method == Method.POST && session.uri == "/pair/submit" -> handleSubmit(session)
        else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
    }

    private fun serveForm(token: String): Response {
        if (token != expectedToken.value || expectedToken.isExpired() || tokenConsumed) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/html; charset=utf-8", EXPIRED_HTML)
        }
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", formHtml(token))
    }

    @Synchronized
    private fun handleSubmit(session: IHTTPSession): Response {
        if (tokenConsumed || expectedToken.isExpired()) {
            return newFixedLengthResponse(Response.Status.GONE, "text/plain; charset=utf-8", "expired")
        }

        val fields = runCatching {
            val body = HashMap<String, String>()
            session.parseBody(body)
            parseUrlEncoded(body["postData"].orEmpty())
        }.getOrElse {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain; charset=utf-8", "invalid form")
        }

        if (fields["token"] != expectedToken.value) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain; charset=utf-8", "bad token")
        }

        val profile = PlaylistProfile(
            listName = fields["listName"].orEmpty().trim(),
            username = if (mode == PairingMode.XTREAM) fields["username"].orEmpty().trim() else "",
            password = if (mode == PairingMode.XTREAM) fields["password"].orEmpty() else "",
            hostUrl = fields["host"].orEmpty().trim(),
        )

        val valid = profile.listName.isNotBlank() && profile.hostUrl.isNotBlank() &&
            (mode == PairingMode.M3U || (profile.username.isNotBlank() && profile.password.isNotBlank()))
        if (!valid) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain; charset=utf-8", "missing required fields")
        }

        // Consume only after the request is syntactically valid. Provider authentication
        // happens on the TV; LoginViewModel creates a fresh QR token if it fails.
        tokenConsumed = true
        onCredentialsReceived(profile)
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", SUCCESS_HTML)
    }

    private fun parseUrlEncoded(raw: String): Map<String, String> = raw.split("&").mapNotNull { pair ->
        val idx = pair.indexOf('=')
        if (idx < 0) null else URLDecoder.decode(pair.substring(0, idx), "UTF-8") to URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
    }.toMap()

    private fun formHtml(token: String): String {
        val xtreamFields = if (mode == PairingMode.XTREAM) """
          <input name="username" placeholder="اسم المستخدم" required>
          <input name="password" type="password" placeholder="كلمة السر" required>
          <input name="host" placeholder="Host / URL" required>
        """ else """
          <input name="host" placeholder="رابط M3U" required>
        """
        val heading = if (mode == PairingMode.XTREAM) "تسجيل Xtream" else "تسجيل M3U URL"
        return """
        <!DOCTYPE html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
        <title>BRO PLUS TV - $heading</title><style>
        body{background:#080d16;color:#fff;font-family:Arial,sans-serif;padding:24px;max-width:620px;margin:auto}h1{color:#f2b90c;font-size:22px}
        p{color:#c7ccd6}input{width:100%;box-sizing:border-box;padding:14px;margin:8px 0;border-radius:9px;border:1px solid #394355;background:#141a24;color:#fff;font-size:16px}
        button{width:100%;padding:15px;margin-top:14px;border:0;border-radius:9px;background:#f2b90c;color:#10151e;font-weight:bold;font-size:16px}
        </style></head><body><h1>BRO PLUS TV — $heading</h1><p>هذه الجلسة صالحة لخمس دقائق ولمرة واحدة.</p>
        <form id="f"><input type="hidden" name="token" value="$token"><input name="listName" placeholder="اسم القائمة" required>$xtreamFields
        <button type="submit">إرسال إلى الشاشة وتسجيل الدخول</button></form>
        <script>document.getElementById('f').addEventListener('submit',function(e){e.preventDefault();var d=new URLSearchParams(new FormData(e.target)).toString();fetch('/pair/submit',{method:'POST',body:d}).then(function(r){document.body.innerHTML=r.ok?'<h1>تم الإرسال ✔</h1><p>جاري تسجيل الدخول على شاشة BRO PLUS TV…</p>':'<h1>انتهت صلاحية الرمز</h1>';});});</script>
        </body></html>
        """.trimIndent()
    }

    private companion object {
        const val EXPIRED_HTML = "<h1 style='font-family:sans-serif'>انتهت صلاحية الرمز. افتح رمزًا جديدًا من BRO PLUS TV.</h1>"
        const val SUCCESS_HTML = "<h1 style='font-family:sans-serif'>تم الإرسال ✔</h1>"
    }
}
