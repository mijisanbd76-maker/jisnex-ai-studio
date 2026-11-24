@file:OptIn(ExperimentalMaterial3Api::class)
package com.jisnex.aistudio

import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.util.Locale

data class Scene(val text: String, val mood: String, val voice: Int, val sec: Int)

class Studio {
    var script by mutableStateOf("")
    var vertical by mutableStateOf(false)
    var scenes by mutableStateOf(listOf<Scene>())
    var music by mutableStateOf<Uri?>(null)
    var overlay by mutableStateOf<Uri?>(null)
}

val voiceNames = listOf("মেয়ে ভয়েস", "ছেলে ১", "ছেলে ২")

fun moodOf(t: String) = when {
    listOf("কাঁদ", "দুঃখ", "কষ্ট", "মৃত্যু", "হারা").any { t.contains(it) } -> "দুঃখ"
    listOf("খুশি", "আনন্দ", "হাসি", "সুখ").any { t.contains(it) } -> "আনন্দ"
    listOf("যুদ্ধ", "ভয়", "বিপদ", "আক্রমণ", "ঝড়").any { t.contains(it) } -> "উত্তেজনা"
    else -> "শান্ত"
}

fun analyze(script: String): List<Scene> =
    script.split(Regex("(?<=[।.!?])\\s+|\\n+")).filter { it.isNotBlank() }.chunked(2).map { g ->
        val t = g.joinToString(" ").trim()
        val words = t.split(Regex("\\s+")).size
        Scene(t, moodOf(t), 0, maxOf(3, (words / 2.5).toInt()))
    }

fun speak(tts: TextToSpeech?, s: Scene) {
    val (p, r) = when (s.mood) {
        "দুঃখ" -> 0.8f to 0.85f
        "আনন্দ" -> 1.15f to 1.05f
        "উত্তেজনা" -> 1.1f to 1.2f
        else -> 1f to 0.95f
    }
    val vp = listOf(1.25f, 1f, 0.8f)[s.voice]
    tts?.setPitch((p * vp).coerceIn(0.5f, 2f))
    tts?.setSpeechRate(r)
    tts?.speak(s.text, TextToSpeech.QUEUE_FLUSH, null, "preview")
}

@Composable
fun rememberTts(): TextToSpeech? {
    val ctx = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(ctx) { st ->
            if (st == TextToSpeech.SUCCESS) { engine?.language = Locale("bn", "BD"); tts = engine }
        }
        onDispose { engine?.stop(); engine?.shutdown() }
    }
    return tts
}

@Composable
fun Page(title: String, nav: NavHostController, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = {
            if (nav.previousBackStackEntry != null) TextButton(onClick = { nav.popBackStack() }) { Text("←") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun Go(label: String, nav: NavHostController, route: String, enabled: Boolean = true) =
    Button(onClick = { nav.navigate(route) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }

class MainActivity : ComponentActivity() {
    private val studio = Studio()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7C4DFF), background = Color(0xFF0E0E16), surface = Color(0xFF181824))) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App(studio) }
            }
        }
    }
}

@Composable
fun App(st: Studio) {
    val nav = rememberNavController()
    NavHost(nav, "home") {
        composable("home") {
            Page("JISNEX AI STUDIO", nav) {
                Text("গল্প থেকে অটো ভিডিও", style = MaterialTheme.typography.headlineSmall)
                Text("স্ক্রিপ্ট দিন, বাকিটা অ্যাপ করবে।")
                Go("নতুন প্রজেক্ট", nav, "script")
                Go("শেষ প্রজেক্ট খুলুন", nav, "editor", st.scenes.isNotEmpty())
            }
        }
        composable("script") {
            Page("স্ক্রিপ্ট", nav) {
                OutlinedTextField(st.script, { st.script = it }, Modifier.fillMaxWidth().weight(1f), label = { Text("গল্প বা স্ক্রিপ্ট লিখুন") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!st.vertical, { st.vertical = false }, { Text("16:9 YouTube") })
                    FilterChip(st.vertical, { st.vertical = true }, { Text("9:16 Shorts") })
                }
                Button(onClick = { st.scenes = analyze(st.script); nav.navigate("analysis") }, enabled = st.script.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("বিশ্লেষণ করো") }
            }
        }
        composable("analysis") {
            Page("সিন বিশ্লেষণ (${st.scenes.size})", nav) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(st.scenes) { i, s ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("সিন ${i + 1} • ${s.mood} • ~${s.sec}s", style = MaterialTheme.typography.labelLarge)
                                Text(s.text)
                            }
                        }
                    }
                }
                Go("ভয়েস স্টুডিও", nav, "voice")
            }
        }
        composable("voice") {
            val tts = rememberTts()
            Page("ভয়েস স্টুডিও", nav) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(st.scenes) { i, s ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("সিন ${i + 1} • ${s.mood}", style = MaterialTheme.typography.labelLarge)
                                Text(s.text, maxLines = 2)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    voiceNames.forEachIndexed { v, n ->
                                        FilterChip(s.voice == v, {
                                            st.scenes = st.scenes.toMutableList().also { it[i] = s.copy(voice = v) }
                                        }, { Text(n) })
                                    }
                                }
                                TextButton(onClick = { speak(tts, s) }) { Text("▶ শুনুন") }
                            }
                        }
                    }
                }
                Go("এডিটর টাইমলাইন", nav, "editor")
            }
        }
        composable("editor") {
            val total = st.scenes.sumOf { it.sec }
            val maxSec = (st.scenes.maxOfOrNull { it.sec } ?: 1).toFloat()
            Page("টাইমলাইন", nav) {
                Text("ফরম্যাট: ${if (st.vertical) "9:16" else "16:9"} • মোট ~${total}s")
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(st.scenes) { i, s ->
                        Text("সিন ${i + 1} • ${voiceNames[s.voice]} • ${s.sec}s", style = MaterialTheme.typography.labelMedium)
                        Box(Modifier.fillMaxWidth((s.sec / maxSec).coerceIn(0.15f, 1f)).height(22.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)))
                    }
                }
                Go("মিউজিক ও ওভারলে", nav, "music")
            }
        }
        composable("music") {
            val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { st.music = it }
            val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { st.overlay = it }
            Page("মিউজিক ও ওভারলে", nav) {
                Button(onClick = { pickAudio.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("ব্যাকগ্রাউন্ড মিউজিক বাছুন") }
                Text(st.music?.lastPathSegment ?: "কোনো মিউজিক নেই")
                Button(onClick = { pickImage.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Like/Subscribe PNG বাছুন") }
                Text(st.overlay?.lastPathSegment ?: "কোনো ওভারলে নেই")
                Spacer(Modifier.weight(1f))
                Go("এক্সপোর্ট", nav, "export")
            }
        }
        composable("export") {
            Page("এক্সপোর্ট", nav) {
                Text("সিন: ${st.scenes.size} • ফরম্যাট: ${if (st.vertical) "9:16" else "16:9"}")
                Text("মিউজিক: ${if (st.music != null) "আছে" else "নেই"} • ওভারলে: ${if (st.overlay != null) "আছে" else "নেই"}")
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("রেন্ডার (ধাপ ৬-এ আসছে)") }
            }
        }
    }
}
