package io.github.ffdvdgd.opluskeymap

import android.os.Bundle
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.function.Consumer
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

// Both screens share the service lifecycle and native shortcut dispatch.
abstract class SettingsPage : ComponentActivity() {
    protected lateinit var app: SettingsApp
    protected var statusMessage by mutableStateOf("")
    protected var preferencesReady by mutableStateOf(false)
    private val shortcuts = ShortcutKeys()
    private val refresh = Runnable {
        statusMessage = app.status()
        preferencesReady = app.preferences() != null
        refreshPreferences()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app = application as SettingsApp
        setContent {
            val theme = remember { ThemeController(ColorSchemeMode.System) }
            MiuixTheme(controller = theme) {
                Scaffold(
                    topBar = {
                        SmallTopAppBar(
                            title = title.toString(),
                            navigationIcon = {
                                if (!isTaskRoot) {
                                    IconButton(
                                        onClick = { finish() },
                                        modifier = Modifier.semantics { contentDescription = "返回" },
                                    ) {
                                        Text("‹", fontSize = 32.sp)
                                    }
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        Text(
                            text = statusMessage,
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                .background(
                                    MiuixTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    RoundedCornerShape(20.dp),
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                        PageContent()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.observe(refresh)
        refresh.run()
    }

    override fun onStop() {
        app.removeObserver(refresh)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) shortcuts.clear()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        handleShortcut(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        (ShortcutKeys.mouseKey(event) != KeyEvent.KEYCODE_UNKNOWN && handleShortcut(event)) ||
            super.dispatchGenericMotionEvent(event)

    private fun handleShortcut(event: InputEvent): Boolean {
        val prefs = app.preferences()
        val action = shortcuts.handle(
            event,
            prefs?.getInt(SettingsApp.RELEASE_KEY, 0) ?: 0,
            prefs?.getInt(SettingsApp.RESET_KEY, 0) ?: 0,
        ) { prefs != null }
        if (action == ShortcutKeys.Action.PASS) return false
        if (action == ShortcutKeys.Action.CONSUME) return true
        val reset = action == ShortcutKeys.Action.RESET
        val completion = Consumer<Int> { result ->
            if (!isDestroyed) {
                val message = when (result) {
                    RESULT_OK -> if (reset) "已松开并复位所有触摸" else "已松开鼠标"
                    RESULT_CANCELED -> "游戏助手未响应，请确认模块已启用。"
                    else -> "操作失败，请查看模块日志。"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
        try {
            if (reset) app.resetTouches(completion) else app.releaseMouse(completion)
        } catch (error: RuntimeException) {
            Toast.makeText(this, "无法发送请求，请重新打开模块后重试。", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    protected abstract fun refreshPreferences()

    @Composable
    protected abstract fun PageContent()
}
