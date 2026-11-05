package com.celoext.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celoext.Graph
import com.celoext.ui.Nav
import com.celoext.ui.components.Ico
import com.celoext.ui.components.Txt
import com.celoext.ui.components.tap
import com.celoext.ui.theme.Ic
import java.util.Calendar
import com.celoext.ui.theme.Oc

/**
 * Default home: a greeting for the time of day, what the agent is running on, four starter cards
 * and the last sessions to continue. Tapping a card only fills the input, nothing is sent.
 */
@Composable
fun DefaultHome(nav: Nav, code: Boolean, onPick: (String) -> Unit) {
    val c = Oc.c
    val hello = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            in 18..22 -> "Good evening"
            else -> "Still up?"
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Txt(hello, size = 30.sp, family = Oc.heading, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Txt(if (code) "What are we building?" else "What can I help with?", size = 18.sp, color = c.textDim)
        Spacer(Modifier.height(14.dp))
        StatusPill(code)
        Spacer(Modifier.height(22.dp))
        ideas(code).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { i ->
                    Column(
                        Modifier.weight(1f).clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp))
                            .tap(scale = 0.97f) { onPick(i.prompt) }.padding(14.dp),
                    ) {
                        Box(Modifier.size(34.dp).clip(Oc.round(10.dp)).background(c.progressTrack), contentAlignment = Alignment.Center) {
                            Ico(i.icon, tint = c.accent, size = 18.dp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Txt(i.title, size = 16.sp, weight = FontWeight.SemiBold)
                        Txt(i.sub, size = 14.sp, color = c.textDim, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Continue(nav, code)
    }
}

/** A small dot, then "Build · Model" and the provider; shows "Connect a provider" when nothing is set up. */
@Composable
private fun StatusPill(code: Boolean) {
    val c = Oc.c
    val prefs = Graph.prefs
    val providers by Graph.catalog.providers.collectAsState()
    val provider = providers.firstOrNull { it.id == prefs.providerId }
    val model = provider?.models?.get(prefs.modelId)
    val name = model?.name?.ifBlank { null } ?: prefs.modelId.ifBlank { null }
    val mode = if (code) prefs.agent.replaceFirstChar { it.uppercase() } else "Chat"
    Row(
        Modifier.clip(CircleShape).background(c.surface).border(1.dp, c.border, CircleShape)
            .tap(scale = 0.97f) { /* the model is changed from the pill in the top bar */ }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (name != null) c.ok else c.warn))
        Spacer(Modifier.width(8.dp))
        Txt(
            if (name != null) "$mode · $name" else if (providers.isEmpty()) "Loading models…" else "No model selected",
            size = 13.sp, color = c.textDim, family = Oc.mono, maxLines = 1,
        )
    }
}

/** The two latest sessions, one tap to open. Hidden when there is nothing to continue. */
@Composable
private fun Continue(nav: Nav, code: Boolean) {
    val c = Oc.c
    val all by Graph.chats.sessions.collectAsState()
    val recent = remember(all, code) {
        val l = if (code) Graph.chats.codeOnly(all) else Graph.chats.chatsOnly(all)
        l.sortedByDescending { it.time.updated }.take(2)
    }
    if (recent.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    Txt("Continue", Modifier.padding(start = 4.dp, bottom = 6.dp), size = 13.sp, color = c.textDim, weight = FontWeight.SemiBold)
    recent.forEach { s ->
        Row(
            Modifier.fillMaxWidth().clip(Oc.round(14.dp)).tap(scale = 0.98f) { nav.openChat(s.id) }.padding(horizontal = 4.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ico(Ic.Clock, tint = c.textDim, size = 18.dp)
            Spacer(Modifier.width(12.dp))
            Txt(s.displayTitle, Modifier.weight(1f), size = 15.sp, maxLines = 1)
            Spacer(Modifier.width(10.dp))
            Txt(ago(s.time.updated), size = 12.sp, color = c.textDim, maxLines = 1)
        }
    }
}
