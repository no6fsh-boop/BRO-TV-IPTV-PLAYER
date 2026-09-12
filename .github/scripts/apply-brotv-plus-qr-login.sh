#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="${PROJECT_DIR:-/tmp/project}"
python3 - <<'PY'
from pathlib import Path
import os
root = Path(os.environ.get('PROJECT_DIR','/tmp/project'))

# 1) QR/session lifetime: 5 minutes, fresh UUID every session.
token = root/'app/src/main/java/com/brotv/iptv/data/pairing/PairingToken.kt'
s = token.read_text()
s = s.replace('const val TTL_MILLIS = 3 * 60 * 1000L', 'const val TTL_MILLIS = 5 * 60 * 1000L')
token.write_text(s)

# 2) Phone page branding/copy. Credentials are still posted only to the TV LAN server.
server = root/'app/src/main/java/com/brotv/iptv/data/pairing/PairingServer.kt'
s = server.read_text()
s = s.replace('<title>BROTV+ - إدخال بيانات الاشتراك</title>', '<title>BRO PLUS TV - تسجيل الدخول من الجوال</title>')
s = s.replace('<h1>BROTV+ — إدخال بيانات الاشتراك</h1>', '<h1>BRO PLUS TV — تسجيل الدخول من الجوال</h1>')
s = s.replace('<button type="submit">إرسال إلى التلفزيون</button>', '<button type="submit">إرسال إلى الشاشة وتسجيل الدخول</button>')
s = s.replace("r.ok ? '<h1 style=\"color:#F2B90C\">تم الإرسال ✔</h1><p>يمكنك إغلاق هذه الصفحة الآن.</p>'", "r.ok ? '<h1 style=\"color:#F2B90C\">تم الإرسال ✔</h1><p>جاري تسجيل الدخول على شاشة BRO PLUS TV…</p>'")
server.write_text(s)

# 3) Pass navigation success callback into QR flow.
screen = root/'app/src/main/java/com/brotv/iptv/ui/screens/login/LoginScreen.kt'
s = screen.read_text()
s = s.replace('onClick = viewModel::openQrSheet,', 'onClick = { viewModel.openQrSheet(onLoginSuccess) },')
screen.write_text(s)

# 4) When phone submits: update Compose state on viewModelScope/Main, close QR, then authenticate immediately.
vm = root/'app/src/main/java/com/brotv/iptv/ui/screens/login/LoginViewModel.kt'
s = vm.read_text()
old = '''    fun openQrSheet() {
        isQrSheetVisible = true
        qrSession = qrPairingManager.startSession { profile ->
            listName = profile.listName
            username = profile.username
            password = profile.password
            hostUrl = profile.hostUrl
            focusedField = LoginField.LOGIN_BUTTON
            isQrSheetVisible = false
        }
    }
'''
new = '''    fun openQrSheet(onLoginSuccess: () -> Unit) {
        qrPairingManager.stopSession()
        isQrSheetVisible = true
        qrSession = qrPairingManager.startSession { profile ->
            viewModelScope.launch {
                listName = profile.listName
                username = profile.username
                password = profile.password
                hostUrl = profile.hostUrl
                focusedField = LoginField.LOGIN_BUTTON
                isQrSheetVisible = false
                qrSession = null
                submitLogin(onLoginSuccess)
            }
        }
    }
'''
if old not in s:
    raise SystemExit('LoginViewModel openQrSheet block not found')
s = s.replace(old, new)
vm.write_text(s)

# Guard rails: old visual text must not survive in touched pairing/login sources.
for p in (server, screen, vm):
    t = p.read_text()
    if 'BROTV+' in t or 'BROTV +' in t:
        raise SystemExit(f'legacy branding remains in {p}')

print('QR auto-login patch applied')
print('Pairing TTL: 5 minutes')
print('Phone CTA: إرسال إلى الشاشة وتسجيل الدخول')
PY
