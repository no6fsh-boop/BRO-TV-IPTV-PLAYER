package com.brotv.iptv.ui.screens.login

import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brotv.iptv.R
import com.brotv.iptv.data.local.SecureCredentialStore
import com.brotv.iptv.data.remote.IptvRepository
import com.brotv.iptv.ui.theme.BroTvColors

@Composable
fun LoginScreen(
    credentialStore: SecureCredentialStore,
    repository: IptvRepository,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModelFactory(credentialStore, repository)),
) {
    LaunchedEffect(viewModel.mode) { viewModel.startQrSession(onLoginSuccess) }
    DisposableEffect(Unit) { onDispose(viewModel::stopQr) }

    Box(Modifier.fillMaxSize().background(BroTvColors.BackgroundNight).border(1.dp, BroTvColors.Gold.copy(alpha = .55f))) {
        Image(painterResource(R.drawable.bg_login_night), null, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color(0xD60A0E14)))

        Row(
            Modifier.fillMaxSize().padding(horizontal = 52.dp, vertical = 34.dp),
            horizontalArrangement = Arrangement.spacedBy(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(.9f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.brotv_logo_gold), "BRO PLUS TV", Modifier.height(58.dp).align(Alignment.Start))
                Spacer(Modifier.weight(1f))
                Text(if (viewModel.mode == LoginMode.XTREAM) "تسجيل Xtream من الجوال" else "تسجيل M3U من الجوال", color = Color.White)
                Spacer(Modifier.height(12.dp))
                val qr = viewModel.qrSession?.qrBitmap
                if (qr != null) {
                    Image(qr.asImageBitmap(), "QR ${viewModel.mode}", Modifier.size(270.dp).background(Color.White, RoundedCornerShape(12.dp)).border(3.dp, BroTvColors.Gold, RoundedCornerShape(12.dp)).padding(8.dp))
                } else {
                    Box(Modifier.size(270.dp).border(2.dp, BroTvColors.Gold, RoundedCornerShape(12.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(viewModel.qrError ?: "جاري تجهيز رمز QR…\nتأكد من اتصال التلفزيون بالشبكة", color = BroTvColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("رمز جديد لكل جلسة • صالح 5 دقائق • استخدام واحد", color = BroTvColors.TextSecondary)
                Spacer(Modifier.weight(1f))
            }

            Column(Modifier.weight(1.1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeTab("Xtream", viewModel.mode == LoginMode.XTREAM, Modifier.weight(1f)) { viewModel.selectMode(LoginMode.XTREAM) }
                    ModeTab("M3U URL", viewModel.mode == LoginMode.M3U, Modifier.weight(1f)) { viewModel.selectMode(LoginMode.M3U) }
                }
                Spacer(Modifier.height(14.dp))
                LoginFormPanel(viewModel, onLoginSuccess, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable private fun ModeTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
        .background(if (selected) BroTvColors.SurfaceElevatedHigh else BroTvColors.SurfaceElevated, RoundedCornerShape(10.dp))
        .border(if (focused || selected) 2.dp else 1.dp, if (focused || selected) BroTvColors.Gold else BroTvColors.Divider, RoundedCornerShape(10.dp))
        .padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text(text, color = Color.White) }
}

@Composable private fun LoginFormPanel(viewModel: LoginViewModel, onLoginSuccess: () -> Unit, modifier: Modifier = Modifier) {
    val listNameFocus = remember { FocusRequester() }; val usernameFocus = remember { FocusRequester() }; val passwordFocus = remember { FocusRequester() }
    val hostFocus = remember { FocusRequester() }; val loginButtonFocus = remember { FocusRequester() }
    LaunchedEffect(viewModel.focusedField, viewModel.mode) {
        when (viewModel.focusedField) {
            LoginField.LIST_NAME -> listNameFocus.requestFocus(); LoginField.USERNAME -> usernameFocus.requestFocus(); LoginField.PASSWORD -> passwordFocus.requestFocus()
            LoginField.HOST -> hostFocus.requestFocus(); LoginField.LOGIN_BUTTON -> loginButtonFocus.requestFocus()
        }
    }
    Column(modifier.background(BroTvColors.SurfaceElevated.copy(alpha = .96f), RoundedCornerShape(16.dp)).padding(26.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Text(if (viewModel.mode == LoginMode.XTREAM) "بيانات Xtream" else "بيانات M3U URL", color = BroTvColors.Gold)
        BroTvField("اسم القائمة", viewModel.listName, { viewModel.updateField(LoginField.LIST_NAME, it) }, listNameFocus, { viewModel.onFieldFocused(LoginField.LIST_NAME) }, viewModel::advanceFocusOnOk)
        if (viewModel.mode == LoginMode.XTREAM) {
            BroTvField("اسم المستخدم", viewModel.username, { viewModel.updateField(LoginField.USERNAME, it) }, usernameFocus, { viewModel.onFieldFocused(LoginField.USERNAME) }, viewModel::advanceFocusOnOk)
            BroTvField("كلمة السر", viewModel.password, { viewModel.updateField(LoginField.PASSWORD, it) }, passwordFocus, { viewModel.onFieldFocused(LoginField.PASSWORD) }, viewModel::advanceFocusOnOk, true)
            BroTvField("Host / URL", viewModel.hostUrl, { viewModel.updateField(LoginField.HOST, it) }, hostFocus, { viewModel.onFieldFocused(LoginField.HOST) }, viewModel::advanceFocusOnOk)
        } else {
            BroTvField("رابط M3U", viewModel.hostUrl, { viewModel.updateField(LoginField.HOST, it) }, hostFocus, { viewModel.onFieldFocused(LoginField.HOST) }, viewModel::advanceFocusOnOk)
        }
        BroTvPrimaryButton(if (viewModel.isSubmitting) "جاري التحقق..." else "تسجيل الدخول", loginButtonFocus, { viewModel.onFieldFocused(LoginField.LOGIN_BUTTON) }) { if (!viewModel.isSubmitting) viewModel.submitLogin(onLoginSuccess) }
        viewModel.loginError?.let { Text(it, color = Color(0xFFFF8A80)) }
    }
}

@Composable private fun BroTvField(label: String, value: String, onValueChange: (String) -> Unit, focusRequester: FocusRequester, onFocused: () -> Unit, onOk: () -> Unit, isPassword: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(value, onValueChange, label = { Text(label, color = BroTvColors.TextSecondary) }, singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { e -> if (e.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && (e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) { onOk(); true } else false }
            .border(if (focused) 2.dp else 1.dp, if (focused) BroTvColors.Gold else BroTvColors.Divider, RoundedCornerShape(10.dp)),
        colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = BroTvColors.Gold, focusedContainerColor = BroTvColors.SurfaceElevatedHigh, unfocusedContainerColor = BroTvColors.SurfaceElevatedHigh, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onOk() }))
}

@Composable private fun BroTvPrimaryButton(text: String, focusRequester: FocusRequester, onFocused: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }.clickable(onClick = onClick)
        .background(if (focused) BroTvColors.Gold else BroTvColors.GoldDeep, RoundedCornerShape(10.dp)).border(if (focused) 2.dp else 0.dp, BroTvColors.Gold, RoundedCornerShape(10.dp)).padding(vertical = 14.dp), contentAlignment = Alignment.Center) { Text(text, color = Color.White) }
}
