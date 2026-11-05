package com.celoext.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celoext.Graph
import com.celoext.runtime.NineRouterState
import com.celoext.ui.Nav
import com.celoext.ui.components.OcSheet
import com.celoext.ui.components.OcSwitch
import com.celoext.ui.components.SectionLabel
import com.celoext.ui.components.SettingsGroup
import com.celoext.ui.components.SettingsRow
import com.celoext.ui.components.SmallButton
import com.celoext.ui.components.Spinner
import com.celoext.ui.components.TopBar
import com.celoext.ui.components.Txt
import com.celoext.ui.theme.Ic
import com.celoext.ui.theme.Oc
import com.celoext.ui.theme.screenBackdrop
import kotlinx.coroutines.launch

/**
 * Runs the 9Router gateway inside the phone's Ubuntu environment (the same one as the OpenCode
 * server) and opens its dashboard, where the user picks free providers and copies the API key
 * that Settings → Providers → 9Router asks for.
 */
@Composable
fun NineRouterScreen(nav: Nav) {
    val c = Oc.c
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val nr = Graph.ninerouter
    val st by nr.state.collectAsState()
    val log by nr.log.collectAsState()
    val prefs = Graph.prefs
    var showLog by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().screenBackdrop("NineRouterScreen1")) {
        TopBar("9Router", left = { com.celoext.ui.components.IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(Oc.round(24.dp)).background(c.surface).padding(20.dp)) {
                val (label, color) = when (val s = st) {
                    is NineRouterState.Running -> "Running · ${s.version}" to c.ok
                    is NineRouterState.Starting -> "Starting…" to c.warn
                    is NineRouterState.Installing -> s.step + (s.progress?.let { p -> " · ${(p * 100).toInt()}%" } ?: "") to c.warn
                    is NineRouterState.Crashed -> s.message to c.danger
                    NineRouterState.NotInstalled -> "Not installed" to c.textDim
                    NineRouterState.Stopped -> "Stopped" to c.textDim
                }
                Txt(label, size = 19.sp, color = color)
                (st as? NineRouterState.Installing)?.takeIf { it.detail.isNotBlank() }?.let {
                    Txt(it.detail, size = 14.sp, color = c.textDim, modifier = Modifier.padding(top = 4.dp))
                }
                Txt("AI gateway · 127.0.0.1:${nr.port}", size = 15.sp, color = c.textDim, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        st is NineRouterState.Installing || installing -> SmallButton("Installing…", {})
                        !nr.installed() -> SmallButton("Install", {
                            installing = true
                            scope.launch {
                                val ok = nr.install()
                                installing = false
                                if (ok) nr.startServer()
                            }
                        }, filled = true)
                        st is NineRouterState.Running || st is NineRouterState.Starting -> {
                            SmallButton("Stop", { nr.stopServer() }, danger = true)
                            SmallButton("Restart", { nr.restartServer() })
                        }
                        else -> {
                            SmallButton("Start", { nr.startServer() }, filled = true)
                            SmallButton("Restart", { nr.restartServer() })
                        }
                    }
                    SmallButton("Log", { showLog = true })
                    if (st is NineRouterState.Running) SmallButton("Dashboard", { nav.push(com.celoext.ui.Route.Preview(nr.dashboardUrl, null)) }, filled = true)
                }
                if (installing) Row(Modifier.padding(top = 14.dp)) { Spinner(22.dp) }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Dashboard login")
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(Oc.round(24.dp)).background(c.surface).padding(20.dp)) {
                Txt(
                    "The dashboard asks for a password on first login. This app generated one for you; the API key you plug into Providers lives in the dashboard under Endpoint / API keys.",
                    size = 14.sp, color = c.textDim, lineHeight = 20.sp,
                )
                Spacer(Modifier.height(10.dp))
                Txt("Password", size = 14.sp, color = c.textDim)
                Txt(nr.password, size = 16.sp, family = Oc.mono, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallButton("Copy password", { clip.setText(AnnotatedString(nr.password)) })
                    if (st is NineRouterState.Running) SmallButton("Open dashboard", { nav.push(com.celoext.ui.Route.Preview(nr.dashboardUrl, null)) }, filled = true)
                }
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                SettingsRow("Start with the app", subtitle = "Runs next to the OpenCode server when the app starts", icon = Ic.Zap, trailing = {
                    OcSwitch(prefs.ninerouterAuto) { prefs.ninerouterAuto = it }
                })
                SettingsRow("How to use", icon = Ic.Info, onClick = {
                    nav.push(com.celoext.ui.Route.Preview("https://github.com/decolua/9router", null))
                }, subtitle = "Docs, providers and combos")
            }
            Txt(
                "9Router routes requests to 40+ AI providers with fallback between them. Free providers (Kiro, OpenCode Free, Vertex) need their own sign-in inside its dashboard; this app only hosts the server.",
                size = 13.sp, color = c.textDim, modifier = Modifier.padding(20.dp), lineHeight = 19.sp,
            )
        }
    }

    if (showLog) OcSheet({ showLog = false }, "9Router log", skipHalf = false) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton("Copy", { clip.setText(AnnotatedString(log.joinToString("\n"))) })
        }
        Column(Modifier.fillMaxWidth().height(480.dp).verticalScroll(rememberScrollState(), reverseScrolling = true).padding(16.dp)) {
            Txt(log.joinToString("\n").ifBlank { "(empty)" }, size = 11.sp, family = Oc.mono, color = c.textDim, lineHeight = 15.sp)
        }
    }
}
