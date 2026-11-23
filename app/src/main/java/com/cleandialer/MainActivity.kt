package com.cleandialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.Telephony
import android.telephony.SmsManager
import android.telecom.TelecomManager
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Bg = Color(0xFF0D1015)
private val Surface1 = Color(0xFF191E26)
private val Surface2 = Color(0xFF232A34)
private val Accent = Color(0xFF55D99A)
private val TextMain = Color(0xFFF3F5F7)
private val TextDim = Color(0xFF929BA7)
private val Missed = Color(0xFFFF7373)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Bg.toArgb()
        window.navigationBarColor = Bg.toArgb()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Surface1, onBackground = TextMain, onSurface = TextMain)) {
                DialerApp()
            }
        }
    }
}

@Composable
fun DialerApp() {
    val ctx = LocalContext.current
    var section by remember { mutableIntStateOf(0) }
    var phoneTab by remember { mutableIntStateOf(0) }
    var number by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var selectedThread by remember { mutableStateOf<SmsThread?>(null) }
    var composeTo by remember { mutableStateOf("") }
    var composeBody by remember { mutableStateOf("") }
    var composing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val dialerRoleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val smsRoleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }

    fun call(n: String) {
        if (n.isBlank()) return
        if (hasPerm(ctx, Manifest.permission.CALL_PHONE)) ctx.startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", n, null)))
        else {
            permissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG))
            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", n, null)))
        }
    }
    @Suppress("DEPRECATION")
    fun requestDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) dialerRoleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        } else {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, ctx.packageName)
            ctx.startActivity(intent)
        }
    }
    @Suppress("DEPRECATION")
    fun requestSms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) smsRoleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
        } else {
            ctx.startActivity(Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, ctx.packageName))
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
        TopNavigation(section) { section = it; selectedThread = null; composing = false }
        Box(Modifier.weight(1f)) {
            when {
                section == 0 && phoneTab == 0 -> Keypad(number, { number += it }, { if (number.isNotEmpty()) number = number.dropLast(1) }, { number = "" }, { call(number) })
                section == 0 -> Recents(refresh, { permissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG)) }, { call(it) })
                selectedThread != null -> ConversationScreen(selectedThread!!, onBack = { selectedThread = null }, onSend = { body -> sendSms(ctx, selectedThread!!.address, body); refresh++ })
                section == 1 && composing -> NewMessageScreen(composeTo, composeBody, { composeTo = it }, { composeBody = it }, { composing = false }, { if (composeTo.isNotBlank() && composeBody.isNotBlank()) { sendSms(ctx, composeTo, composeBody); composeBody = ""; composeTo = ""; composing = false; refresh++ } })
                else -> InboxScreen(refresh, { selectedThread = it }, { composing = true; composeTo = ""; composeBody = "" }, { composing = true; composeTo = it; composeBody = "" })
            }
        }
        if (section == 0) BottomTabs(phoneTab) { phoneTab = it }
        if (section == 1 && selectedThread == null && composeTo.isBlank()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { requestSms() }, modifier = Modifier.weight(1f)) { Text(if (isDefaultSms(ctx)) "Default SMS app ✓" else "Make default SMS app") }
                OutlinedButton(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.RECEIVE_MMS)) }, modifier = Modifier.weight(1f)) { Text("Permissions") }
            }
        }
        if (section == 0) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedButton(onClick = { requestDialer() }, modifier = Modifier.fillMaxWidth()) { Text(if (isDefaultDialer(ctx)) "Default phone app ✓" else "Make default phone app") }
            }
        }
    }
}

@Composable
private fun TopNavigation(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).clip(RoundedCornerShape(18.dp)).background(Surface1).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf("Phone", "Messages").forEachIndexed { i, label ->
            Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (selected == i) Surface2 else Color.Transparent).clickable { onSelect(i) }.padding(vertical = 13.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(if (i == 0) Icons.Filled.Call else Icons.Filled.Send, null, tint = if (selected == i) Accent else TextDim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(label, color = if (selected == i) TextMain else TextDim, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        listOf("Keypad", "Recents").forEachIndexed { i, label -> Text(label, Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) }.wrapContentHeight(Alignment.CenterVertically), color = if (selected == i) Accent else TextDim, textAlign = TextAlign.Center, fontSize = 15.sp, fontWeight = if (selected == i) FontWeight.SemiBold else FontWeight.Normal) }
    }
}

private val keys = listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to "")

@Composable
private fun Keypad(number: String, onDigit: (String) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit, onCall: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(number, color = TextMain, fontSize = if (number.length > 12) 28.sp else 40.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center, maxLines = 1) }
        for (r in 0 until 4) Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (c in 0 until 3) { val (digit, letters) = keys[r * 3 + c]; Box(Modifier.size(76.dp).clip(CircleShape).background(Surface1).clickable { onDigit(digit) }, contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(digit, color = TextMain, fontSize = 27.sp, fontWeight = FontWeight.Light); if (letters.isNotEmpty()) Text(letters, color = TextDim, fontSize = 10.sp, letterSpacing = 1.5.sp) } } }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(76.dp)); Box(Modifier.size(76.dp).clip(CircleShape).background(Accent).clickable { onCall() }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Call, "Call", tint = Bg, modifier = Modifier.size(30.dp)) }
            Box(Modifier.size(76.dp).clickable { if (number.isNotEmpty()) onBackspace() }, contentAlignment = Alignment.Center) { if (number.isNotEmpty()) Text("⌫", color = TextDim, fontSize = 26.sp, modifier = Modifier.clickable { onClear() }) }
        }
    }
}

private data class CallEntry(val number: String, val name: String?, val type: Int, val date: Long)
@Composable
private fun Recents(refreshKey: Int, onRequestPermission: () -> Unit, onCall: (String) -> Unit) {
    val ctx = LocalContext.current; val granted = hasPerm(ctx, Manifest.permission.READ_CALL_LOG)
    val entries = remember(refreshKey, granted) { if (granted) loadCalls(ctx) else emptyList() }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Recent calls", color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 20.dp, bottom = 12.dp))
        if (!granted) { Text("Allow call history access to see recent calls.", color = TextDim); Button(onClick = onRequestPermission) { Text("Allow access") } }
        else if (entries.isEmpty()) Text("No recent calls", color = TextDim)
        else LazyColumn { items(entries) { e -> Row(Modifier.fillMaxWidth().clickable { onCall(e.number) }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(e.name?.takeIf { it.isNotBlank() } ?: e.number, color = if (e.type == CallLog.Calls.MISSED_TYPE) Missed else TextMain, fontSize = 17.sp); Text(when(e.type) { CallLog.Calls.OUTGOING_TYPE -> "Outgoing"; CallLog.Calls.MISSED_TYPE -> "Missed"; else -> "Incoming" }, color = TextDim, fontSize = 13.sp) }; Text(DateUtils.getRelativeTimeSpanString(e.date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(), color = TextDim, fontSize = 12.sp) } } }
    }
}

private data class SmsThread(val threadId: Long, val address: String, val snippet: String, val date: Long, val type: Int)
@Composable
private fun InboxScreen(refreshKey: Int, onOpen: (SmsThread) -> Unit, onCompose: () -> Unit, onNewTo: (String) -> Unit) {
    val ctx = LocalContext.current; val granted = isDefaultSms(ctx) || hasPerm(ctx, Manifest.permission.READ_SMS)
    val threads = remember(refreshKey, granted) { if (granted) loadSmsThreads(ctx) else emptyList() }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text("Messages", Modifier.weight(1f), color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.Light); Button(onClick = onCompose, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)) { Icon(Icons.Filled.Send, null); Spacer(Modifier.width(6.dp)); Text("New") } }
        if (!granted) Text("Set Clean Dialer as your default SMS app to manage your inbox, receive texts, and send directly.", color = TextDim)
        else if (threads.isEmpty()) Text("Your conversations will appear here.", color = TextDim)
        LazyColumn { items(threads) { t -> Row(Modifier.fillMaxWidth().clickable { onOpen(t) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(46.dp).clip(CircleShape).background(Surface2), contentAlignment = Alignment.Center) { Text(t.address.take(1).uppercase(), color = Accent, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(t.address, color = TextMain, fontWeight = FontWeight.SemiBold); Text(t.snippet, color = TextDim, maxLines = 1, fontSize = 13.sp) }; Text(DateUtils.getRelativeTimeSpanString(t.date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(), color = TextDim, fontSize = 11.sp) } } }
    }
}

@Composable
private fun ConversationScreen(thread: SmsThread, onBack: () -> Unit, onSend: (String) -> Unit) {
    val ctx = LocalContext.current; var body by remember { mutableStateOf("") }
    val messages = remember(thread.threadId) { loadConversation(ctx, thread.threadId) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Surface1).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text("‹", Modifier.clickable { onBack() }, color = Accent, fontSize = 30.sp); Spacer(Modifier.width(12.dp)); Column { Text(thread.address, color = TextMain, fontWeight = FontWeight.SemiBold); Text("SMS conversation", color = TextDim, fontSize = 12.sp) } }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(messages) { m -> Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.second) Arrangement.End else Arrangement.Start) { Text(m.first, Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(18.dp)).background(if (m.second) Color(0xFF245E48) else Surface1).padding(12.dp), color = TextMain) } } }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(body, { body = it }, Modifier.weight(1f), placeholder = { Text("Text message…") }, shape = RoundedCornerShape(24.dp), maxLines = 4); Spacer(Modifier.width(8.dp)); IconButton(onClick = { if (body.isNotBlank()) { onSend(body); body = "" } }) { Icon(Icons.Filled.Send, "Send", tint = Accent) } }
    }
}

@Composable
private fun NewMessageScreen(to: String, body: String, onTo: (String) -> Unit, onBody: (String) -> Unit, onBack: () -> Unit, onSend: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("‹", Modifier.clickable { onBack() }, color = Accent, fontSize = 30.sp); Spacer(Modifier.width(12.dp)); Text("New message", color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.Light) }; Spacer(Modifier.height(18.dp)); OutlinedTextField(to, onTo, Modifier.fillMaxWidth(), label = { Text("To: phone number") }, singleLine = true); Spacer(Modifier.height(12.dp)); OutlinedTextField(body, onBody, Modifier.fillMaxWidth().weight(1f), label = { Text("Message") }); Button(onClick = onSend, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)) { Icon(Icons.Filled.Send, null); Spacer(Modifier.width(8.dp)); Text("Send SMS") } }
}

private fun hasPerm(ctx: Context, perm: String) = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
private fun isDefaultSms(ctx: Context) = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName
@Suppress("DEPRECATION")
private fun isDefaultDialer(ctx: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ctx.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER) else (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage == ctx.packageName
private fun loadCalls(ctx: Context): List<CallEntry> { val out = mutableListOf<CallEntry>(); try { ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE), null, null, CallLog.Calls.DATE + " DESC")?.use { c -> while (c.moveToNext() && out.size < 60) out.add(CallEntry(c.getString(0) ?: continue, c.getString(1), c.getInt(2), c.getLong(3))) } } catch (_: Exception) {}; return out }
private fun loadSmsThreads(ctx: Context): List<SmsThread> { val out = mutableListOf<SmsThread>(); try { ctx.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE), null, null, Telephony.Sms.DATE + " DESC")?.use { c -> val seen = hashSetOf<Long>(); while (c.moveToNext() && out.size < 100) { val id = c.getLong(0); if (seen.add(id)) out.add(SmsThread(id, c.getString(1) ?: "Unknown", c.getString(2) ?: "", c.getLong(3), c.getInt(4))) } } } catch (_: Exception) {}; return out }
private fun loadConversation(ctx: Context, threadId: Long): List<Pair<String, Boolean>> { val out = mutableListOf<Pair<String, Boolean>>(); try { ctx.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.BODY, Telephony.Sms.TYPE), Telephony.Sms.THREAD_ID + "=?", arrayOf(threadId.toString()), Telephony.Sms.DATE + " ASC")?.use { c -> while (c.moveToNext()) out.add((c.getString(0) ?: "") to (c.getInt(1) == Telephony.Sms.MESSAGE_TYPE_SENT)) } } catch (_: Exception) {}; return out }
@Suppress("DEPRECATION")
private fun sendSms(ctx: Context, address: String, body: String) { if (address.isBlank() || body.isBlank()) return; try { val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault(); val parts = manager.divideMessage(body); if (parts.size > 1) manager.sendMultipartTextMessage(address, null, parts, null, null) else manager.sendTextMessage(address, null, body, null, null); if (isDefaultSms(ctx)) { val values = android.content.ContentValues().apply { put(Telephony.Sms.ADDRESS, address); put(Telephony.Sms.BODY, body); put(Telephony.Sms.DATE, System.currentTimeMillis()); put(Telephony.Sms.READ, 1); put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT) }; ctx.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values) } } catch (_: Exception) { val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(address)}")).putExtra("sms_body", body); ctx.startActivity(intent) } }
