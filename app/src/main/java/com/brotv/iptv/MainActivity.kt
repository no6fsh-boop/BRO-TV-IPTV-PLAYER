package com.brotv.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.brotv.iptv.data.local.SecureCredentialStore
import com.brotv.iptv.navigation.BroTvNavGraph
import com.brotv.iptv.ui.theme.BroTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val credentialStore = SecureCredentialStore(applicationContext)

        setContent {
            BroTvTheme {
                BroTvNavGraph(
                    credentialStore = credentialStore,
                )
            }
        }
    }
}
