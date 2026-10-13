package io.github.ffdvdgd.opluskeymap

import android.content.Intent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.window.WindowDialog

// Configuration and touch operations remain in SettingsApp.
class SettingsActivity : SettingsPage() {
    private var gamesCount by mutableIntStateOf(0)
    private var releaseKey by mutableIntStateOf(KeyEvent.KEYCODE_UNKNOWN)
    private var resetKey by mutableIntStateOf(KeyEvent.KEYCODE_UNKNOWN)
    private var saving by mutableStateOf(false)
    private var resetting by mutableStateOf(false)
    private var binding by mutableStateOf(SettingsApp.RELEASE_KEY)
    private var bindingVisible by mutableStateOf(false)
    private var capturedKey = KeyEvent.KEYCODE_UNKNOWN

    override fun refreshPreferences() {
        val prefs = app.preferences()
        gamesCount = prefs?.getStringSet(SettingsApp.GAMES, emptySet())?.size ?: 0
        releaseKey = prefs?.getInt(SettingsApp.RELEASE_KEY, KeyEvent.KEYCODE_UNKNOWN) ?: 0
        resetKey = prefs?.getInt(SettingsApp.RESET_KEY, KeyEvent.KEYCODE_UNKNOWN) ?: 0
    }

    @Composable
    override fun PageContent() {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val wide = maxWidth >= 840.dp
            Column(
                Modifier.widthIn(max = if (wide) 1200.dp else 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            ) {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        MouseSettings(Modifier.weight(1f))
                        RecoverySettings(Modifier.weight(1f))
                    }
                } else {
                    MouseSettings(Modifier.fillMaxWidth())
                    RecoverySettings(Modifier.fillMaxWidth().padding(top = 16.dp))
                }
            }
        }
        BindingDialog()
    }

    @Composable
    private fun MouseSettings(modifier: Modifier) {
        Column(modifier) {
            SmallTitle("鼠标移动")
            Card {
                ArrowPreference(
                    title = "保持鼠标按下",
                    summary = if (preferencesReady) "已选择 $gamesCount 个游戏" else "设置暂不可读取",
                    onClick = { startActivity(Intent(this@SettingsActivity, GameSelectionActivity::class.java)) },
                )
                ArrowPreference(
                    title = "手动松开快捷键",
                    summary = if (preferencesReady) keyName(releaseKey) else "设置暂不可读取",
                    enabled = preferencesReady && !saving,
                    onClick = { chooseShortcut(SettingsApp.RELEASE_KEY) },
                )
            }
        }
    }

    @Composable
    private fun RecoverySettings(modifier: Modifier) {
        Column(modifier) {
            SmallTitle("故障恢复")
            Card {
                ArrowPreference(
                    title = "强制复位快捷键",
                    summary = if (preferencesReady) keyName(resetKey) else "设置暂不可读取",
                    enabled = preferencesReady && !saving,
                    onClick = { chooseShortcut(SettingsApp.RESET_KEY) },
                )
                TextButton(
                    text = if (resetting) "正在复位…" else "强制松开并复位所有触摸\n（存在未松开触摸点时使用）",
                    onClick = { resetTouches() },
                    enabled = !resetting,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    private fun resetTouches() {
        resetting = true
        statusMessage = "正在复位…"
        try {
            app.resetTouches { result ->
                resetting = false
                if (!isDestroyed) {
                    statusMessage = when (result) {
                        RESULT_OK -> "已松开并复位所有触摸"
                        RESULT_CANCELED -> "游戏助手未响应，请先进入游戏并确认模块已启用。"
                        else -> "复位失败，请查看模块日志。"
                    }
                }
            }
        } catch (error: RuntimeException) {
            resetting = false
            statusMessage = "无法发送复位请求，请重新打开模块后重试。"
        }
    }

    private fun chooseShortcut(key: String) {
        capturedKey = KeyEvent.KEYCODE_UNKNOWN
        binding = key
        bindingVisible = true
    }

    @Composable
    private fun BindingDialog() {
        WindowDialog(
            show = bindingVisible,
            title = "设置" + if (binding == SettingsApp.RELEASE_KEY) "手动松开快捷键" else "强制复位快捷键",
            summary = "按下要绑定的按键。绑定按键在模块界面和游戏中仅供模块使用。",
            onDismissRequest = { bindingVisible = false },
        ) {
            val window = (LocalView.current.parent as DialogWindowProvider).window
            DisposableEffect(window) {
                val previous = window.callback
                val callback = object : Window.Callback by previous {
                    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                        capture(event.keyCode, event.action == KeyEvent.ACTION_DOWN,
                            event.action == KeyEvent.ACTION_UP, event.repeatCount == 0)
                        return true
                    }

                    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
                        val code = ShortcutKeys.mouseKey(event)
                        if (code == KeyEvent.KEYCODE_UNKNOWN) return previous.dispatchGenericMotionEvent(event)
                        capture(code, event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS,
                            event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE, true)
                        return true
                    }
                }
                window.callback = callback
                onDispose {
                    if (window.callback === callback) window.callback = previous
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "清除",
                    onClick = {
                        saveShortcut(binding, KeyEvent.KEYCODE_UNKNOWN)
                        bindingVisible = false
                    },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                )
                TextButton(text = "取消", onClick = { bindingVisible = false }, modifier = Modifier.weight(1f))
            }
        }
    }

    private fun capture(code: Int, down: Boolean, up: Boolean, first: Boolean) {
        if (!bindingVisible) return
        if (!saving && capturedKey == KeyEvent.KEYCODE_UNKNOWN && code != KeyEvent.KEYCODE_UNKNOWN && down && first) {
            capturedKey = code
            saveShortcut(binding, code)
        }
        // Keep the DOWN in the dialog; closing now could execute the new binding in the Activity.
        if (capturedKey == code && up) bindingVisible = false
    }

    private fun saveShortcut(key: String, code: Int) {
        saving = true
        statusMessage = "正在保存…"
        app.saveShortcut(key, code) {
            saving = false
            if (!isDestroyed) {
                statusMessage = app.status()
                preferencesReady = app.preferences() != null
                refreshPreferences()
            }
        }
    }

    private fun keyName(code: Int): String = if (code == KeyEvent.KEYCODE_UNKNOWN) "未设置"
        else KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
}
