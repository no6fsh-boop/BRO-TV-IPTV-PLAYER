#!/usr/bin/env bash
set -euo pipefail
: "${PROJECT_DIR:?PROJECT_DIR is required}"
python3 - <<'PY'
from pathlib import Path
root=Path(__import__('os').environ['PROJECT_DIR'])
main=root/'app/src/main/java/com/brotv/iptv/MainActivity.kt'
nav=root/'app/src/main/java/com/brotv/iptv/navigation/BroTvNavGraph.kt'
text=main.read_text()
old='''        val credentialStore = SecureCredentialStore(applicationContext)\n\n        setContent {\n            BroTvTheme {\n                BroTvNavGraph(\n                    credentialStore = credentialStore,\n                )\n            }\n        }'''
new='''        val credentialStore = SecureCredentialStore(applicationContext)\n        val demoEnabled = intent.getBooleanExtra("BRO_DEMO", false)\n        if (demoEnabled) {\n            credentialStore.saveProfile(\n                com.brotv.iptv.data.model.PlaylistProfile(\n                    listName = "BRO PLUS DEMO",\n                    username = "demo",\n                    password = "demo",\n                    hostUrl = "http://10.0.2.2:18080",\n                )\n            )\n        }\n        val demoRoute = if (demoEnabled) intent.getStringExtra("BRO_DEMO_ROUTE") else null\n\n        setContent {\n            BroTvTheme {\n                BroTvNavGraph(\n                    credentialStore = credentialStore,\n                    startDestinationOverride = demoRoute,\n                )\n            }\n        }'''
if old not in text:
    raise SystemExit('MainActivity anchor not found')
main.write_text(text.replace(old,new,1))

text=nav.read_text()
old='''fun BroTvNavGraph(\n    credentialStore: SecureCredentialStore,\n    navController: NavHostController = rememberNavController(),\n) {'''
new='''fun BroTvNavGraph(\n    credentialStore: SecureCredentialStore,\n    navController: NavHostController = rememberNavController(),\n    startDestinationOverride: String? = null,\n) {'''
if old not in text:
    raise SystemExit('BroTvNavGraph signature anchor not found')
text=text.replace(old,new,1)
old2='''    val startDestination = if (credentialStore.loadActiveProfile() != null) BroTvDestinations.HOME else BroTvDestinations.LOGIN'''
new2='''    val startDestination = startDestinationOverride ?: if (credentialStore.loadActiveProfile() != null) BroTvDestinations.HOME else BroTvDestinations.LOGIN'''
if old2 not in text:
    raise SystemExit('startDestination anchor not found')
nav.write_text(text.replace(old2,new2,1))
print('Demo hook applied (explicit intent extra, QA build only).')
PY
