package io.github.ffdvdgd.opluskeymap

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import java.util.Locale
import java.util.TreeMap
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference

// Selection still saves immediately; only the rendering changes to Miuix.
class GameSelectionActivity : SettingsPage() {
    private data class AppChoice(val pkg: String, val label: String, val icon: ImageBitmap)

    private var apps by mutableStateOf<List<AppChoice>>(emptyList())
    private var selected by mutableStateOf<Set<String>>(emptySet())
    private var query by mutableStateOf("")
    private var saving by mutableStateOf(false)

    override fun refreshPreferences() {
        selected = app.preferences()?.getStringSet(SettingsApp.GAMES, emptySet())?.toSet().orEmpty()
        val labels = TreeMap<String, String>()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        for (info in packageManager.queryIntentActivities(launcher, 0)) {
            val pkg = info.activityInfo.packageName
            if (pkg != packageName) labels[pkg] = info.loadLabel(packageManager).toString()
        }
        // Keep selected packages visible if their launcher was removed.
        for (pkg in selected) labels.putIfAbsent(pkg, pkg)
        apps = labels.map { (pkg, label) ->
            val icon = try {
                packageManager.getApplicationIcon(pkg)
            } catch (missing: PackageManager.NameNotFoundException) {
                packageManager.defaultActivityIcon
            }
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, bitmap.width, bitmap.height)
            icon.draw(Canvas(bitmap))
            AppChoice(pkg, label, bitmap.asImageBitmap())
        }
        orderApps()
    }

    @Composable
    override fun PageContent() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxSize().padding(horizontal = 24.dp)) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "搜索应用名或包名",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SmallTitle(if (preferencesReady) "已选择 ${selected.size} 个应用" else "名单暂不可读取")
                val value = query.trim().lowercase(Locale.ROOT)
                val visible = apps.filter { "${it.label}\n${it.pkg}".lowercase(Locale.ROOT).contains(value) }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(visible, key = { it.pkg }) { item ->
                        Card(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                            CheckboxPreference(
                                title = item.label,
                                summary = item.pkg,
                                checked = item.pkg in selected,
                                onCheckedChange = { checked -> select(item.pkg, checked) },
                                enabled = preferencesReady && !saving,
                                checkboxLocation = CheckboxLocation.End,
                                startAction = {
                                    Image(
                                        bitmap = item.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                                    )
                                },
                            )
                        }
                    }
                    if (visible.isEmpty()) item {
                        Text("没有匹配的应用", modifier = Modifier.padding(24.dp))
                    }
                }
            }
        }
    }

    private fun select(pkg: String, checked: Boolean) {
        val next = selected.toMutableSet()
        if (checked) next.add(pkg) else next.remove(pkg)
        selected = next
        saving = true
        statusMessage = "正在保存…"
        app.save(next) { saved ->
            saving = false
            if (!isDestroyed) {
                statusMessage = if (saved) app.status() else "保存失败。请结束并重新打开本模块后确认名单。"
                preferencesReady = app.preferences() != null
                if (saved) orderApps()
            }
        }
    }

    private fun orderApps() {
        apps = apps.sortedWith(compareByDescending<AppChoice> { it.pkg in selected }.thenComparator { a, b ->
            "${a.label}\n${a.pkg}".compareTo("${b.label}\n${b.pkg}", ignoreCase = true)
        })
    }
}
