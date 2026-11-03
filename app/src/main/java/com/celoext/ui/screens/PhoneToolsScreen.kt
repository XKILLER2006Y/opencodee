package com.celoext.ui.screens

import com.celoext.ui.theme.screenBackdrop
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celoext.Graph
import com.celoext.api.obj
import com.celoext.api.str
import com.celoext.data.PhoneBridge
import com.celoext.ui.Nav
import com.celoext.ui.components.IconBtn
import com.celoext.ui.components.OcSwitch
import com.celoext.ui.components.SectionLabel
import com.celoext.ui.components.SettingsGroup
import com.celoext.ui.components.SettingsRow
import com.celoext.ui.components.TopBar
import com.celoext.ui.components.Txt
import com.celoext.ui.theme.Ic
import com.celoext.ui.theme.Oc
import kotlinx.coroutines.launch

/** What the agent can do on the phone: the "phone" MCP server the app runs itself. */
@Composable
fun PhoneToolsScreen(nav: Nav) {
    val c = Oc.c
    val ctx = LocalContext.current
    val prefs = Graph.prefs
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var rev by remember { mutableIntStateOf(0) }
    LaunchedEffect(prefs.phoneTools, rev) {
        status = runCatching { Graph.api.mcpStatus().obj(PhoneBridge.NAME)?.str("status") }.getOrNull()
    }
    val storageOk = ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    val askStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { rev++ }
    val off = prefs.phoneToolsOff.split(',').filter { it.isNotBlank() }.toSet()

    Column(Modifier.fillMaxSize().screenBackdrop("PhoneToolsScreen1")) {
        TopBar("Phone tools", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            Txt(
                "OpenCode runs on this phone, so it can also act on it: remind you, read or fill the clipboard, take a photo, put files into Downloads and show the sites it builds.",
                Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp), size = 15.sp, color = c.textDim, lineHeight = 21.sp,
            )
            SettingsGroup {
                SettingsRow(
                    "Let OpenCode use this phone", icon = Ic.Phone,
                    subtitle = when {
                        Graph.devices.isRemote -> "Only for the server on this phone"
                        !prefs.phoneTools -> "Off"
                        status == "connected" -> "Connected · ${PhoneBridge.enabledTools().size} tools"
                        status == null -> "Starts with the server"
                        else -> "Status: $status"
                    },
                    trailing = { OcSwitch(prefs.phoneTools) { prefs.phoneTools = it; scope.launch { PhoneBridge.register(); rev++ } } },
                )
                if (!storageOk) SettingsRow("Allow storage", subtitle = "Lets OpenCode read and write /sdcard", icon = Ic.Folder, chevron = true, onClick = {
                    askStorage.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                })
            }
            if (prefs.phoneTools) {
                SectionLabel("Tools")
                SettingsGroup {
                    PhoneBridge.all.forEach { t ->
                        SettingsRow(t.title, subtitle = t.desc.substringBefore(". ").removeSuffix("."), trailing = {
                            OcSwitch(t.name !in off) { on -> prefs.phoneToolsOff = (if (on) off - t.name else off + t.name).joinToString(",") }
                        })
                    }
                }
                Txt(
                    "Try: “remind me in 20 minutes to stretch”, “take a photo of the board and turn it into notes”, “save the report to Downloads”.\n\nCamera, clipboard and links work while the app is open: Android doesn't let apps do that from the background.",
                    Modifier.padding(20.dp), size = 13.sp, color = c.textDim, lineHeight = 19.sp,
                )
            }
        }
    }
}
