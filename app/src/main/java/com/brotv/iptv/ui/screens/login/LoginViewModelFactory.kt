package com.brotv.iptv.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.brotv.iptv.data.local.SecureCredentialStore
import com.brotv.iptv.data.remote.IptvRepository

class LoginViewModelFactory(
    private val credentialStore: SecureCredentialStore,
    private val repository: IptvRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = LoginViewModel(credentialStore, repository) as T
}
